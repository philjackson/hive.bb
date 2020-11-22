#!/usr/bin/env bb

(require '[babashka.curl :as curl]
         '[clojure.pprint :refer [pprint]])

(import 'java.time.format.DateTimeFormatter
        'java.time.ZonedDateTime)

(def beekeeper-base "https://beekeeper.hivehome.com")

(defn extract-config [data]
  (select-keys data [:token :accessToken :refreshToken]))

;; location of the configuration file
(def config-filename (str (or (System/getenv "XDG_CONFIG_HOME")
                              (str (System/getenv "HOME") "/.config"))
                          "/hive.edn"))

(defn now-gmt []
  (.format
   (ZonedDateTime/now)
   (DateTimeFormatter/ofPattern "EEE, dd MMM yyyy HH:mm:ss z")))

(defn write-config [config]
  (spit config-filename (pr-str config))
  config)

(declare hive-post)

(defn hive-refresh-token [config]
  (println "Refreshing token.")
  (hive-post config
             (str beekeeper-base "/1.0/cognito/refresh-token")
             {:body (json/generate-string config)}))

(defn hive-call [method config url options]
  (let [res (method url (merge options {:throw false
                                        :headers (cond-> {"Content-Type" "application/json"
                                                          "Accept" "application/json"
                                                          "Date" (now-gmt)}
                                                   (:auth? options)
                                                   (assoc "authorization" (:token config)))}))
        status (:status res)
        body (json/parse-string (:body res) keyword)]
    (cond
      ;; we need to refresh the auth token
      (and (= body {:error "NOT_AUTHORIZED"})
           (not (:no-refresh? options)))
      (do
        (-> (hive-refresh-token config)
            extract-config
            write-config)
        (hive-call method config url (assoc options :no-refresh? true)))

      (and (>= status 200) (< status 300))
      body

      :else
      (do
        body
        (System/exit 1)))))

(def hive-get (partial hive-call curl/get))
(def hive-post (partial hive-call curl/post))

(defn product-url [product]
  (str beekeeper-base
       "/1.0/nodes/"
       (:type product)
       "/"
       (:id product)))

(defn authenticate [config args]
  (let [{:keys [options summary errors]}
        (tools.cli/parse-opts
         args [["-u" "--username USERNAME" "Hive username - required"
                :missing "Username missing."]
               ["-p" "--password PASSWORD" "Hive password - required"
                :missing "Password missing."]])]
    (if (not (seq errors))
      (do
        (-> (hive-post config
                       (str beekeeper-base "/1.0/cognito/login")
                       {:body (json/generate-string (select-keys options [:username :password]))})
            extract-config
            write-config)
        (println "That's you authenticated. Good on you."))
      (do
        (doall (map println errors))
        (println summary)
        (System/exit 1)))))

(defn products [config args]
  (->> (hive-get config
                 (str beekeeper-base "/1.0/auth/admin-login")
                 {:auth? false
                  :body (json/generate-string {:token (:token config)
                                               :products true})})
       :products
       (group-by #(get-in % [:state :name]))
       (map (fn [[k [v]]] [k v]))
       (into {})))

(defn lamp-brightness [config [amount]]
  (let [amount (edn/read-string amount)
        prods (products config [])
        lamp (get prods "study lamp")
        brightness (get-in lamp [:state :brightness])]
    (hive-post config
               (product-url lamp)
               {:auth? true
                :body (json/generate-string {:brightness (+ brightness amount)})})))

(defn set-lamp-status [config status]
  (let [prods (products config [])
        lamp (get prods "study lamp")]
    (hive-post config
               (product-url lamp)
               {:auth? true
                :body (json/generate-string (if status
                                              {:status "ON"}
                                              {:status "OFF"}))})))

(defn lamp-toggle [config args]
  (let [prods (products config [])
        lamp (get prods "study lamp")
        status (get-in lamp [:state :status])]
    (set-lamp-status config (not= status "ON"))))

(def cli-options
  [["-v" nil "Verbosity level" :default 0 :update-fn inc]])

#_(def *command-line-args* ["lamp-toggle"])

(let [options (tools.cli/parse-opts *command-line-args* cli-options :in-order true)
      config (if (.exists (io/file config-filename))
               (edn/read-string (slurp config-filename))
               {})]
  (case (first (:arguments options))
    "authenticate" (authenticate config (rest (:arguments options)))
    "products" (products config (rest (:arguments options)))

    "lamp-toggle" (lamp-toggle config (rest (:arguments options)))
    "lamp-on" (set-lamp-status config true)
    "lamp-off" (set-lamp-status config false)

    "lamp-brightness" (lamp-brightness config (rest (:arguments options)))))
