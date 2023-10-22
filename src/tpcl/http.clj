(ns tpcl.http
  (:require [aleph.http :as ah]
            [babashka.fs :as fs]
            [reitit.ring :as ring]
            [simple-cors.aleph.middleware :as cors]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.coercion.spec :as rcs]
            [reitit.ring.coercion :as rrc]
            [cheshire.core :as json]
            [muuntaja.core :as m]
            [taoensso.timbre :as log]))

(def template-path "templates")

(def cors-config {:cors-config {:allowed-request-methods [:post :get :put :delete]
                                :allowed-request-headers ["Authorization" "Content-Type"]
                                :origins                 "*"}})

;; middles
(def reitit-options
  {:data {:muuntaja   m/instance
          :coercion   rcs/coercion
          :middleware [muuntaja/format-middleware
                       rrc/coerce-exceptions-middleware
                       rrc/coerce-request-middleware
                       rrc/coerce-response-middleware]}})

(def app
  "router handler"
  (ring/ring-handler
    (ring/router
      [["" {:no-doc true}
        ["/swagger.json" {:get (swagger/create-swagger-handler)}]
        ["/swagger" {:get {:handler
                           (fn [_req] {:status  302
                                       :headers {"location" "/swagger/index.html"}})}}]
        ["/swagger/*" {:get (swagger-ui/create-swagger-ui-handler)}]]
       ["/template/:name" {:get {:summary    "get template"
                                 :parameters {:path {:name string?}}
                                 :handler    (fn [req]
                                               (let [{{{:keys [name]} :path} :parameters} req
                                                     file-temp-path (fs/path template-path name)
                                                     ext (fs/extension file-temp-path)
                                                     file-path (if (empty? ext)
                                                                 (let [parent (fs/parent file-temp-path)
                                                                       filename (fs/file-name file-temp-path)]
                                                                   (fs/path parent (str filename ".json")))
                                                                 file-temp-path)
                                                     ;; the extension (without dot), will be used for splitting
                                                     file-content (if (= (fs/extension file-path) "json")
                                                                    (if (fs/exists? file-path)
                                                                      (try
                                                                        {:code    200
                                                                         :content (json/decode (slurp (str file-path)) true)}
                                                                        (catch Exception e
                                                                          {:code  500
                                                                           :error (.getMessage e)}))
                                                                      {:code  404
                                                                       :error (str "template not found for file " file-path)})
                                                                    {:code  400
                                                                     :error "not a valid template extension"})
                                                     code (:code file-content)
                                                     content (:content file-content)
                                                     error (:error file-content)]
                                                 (if (nil? error)
                                                   {:status code
                                                    :body   content}
                                                   {:status code
                                                    :body   {:error error}})))}}]
       ["/"
        {:get {:summary "hello world"
               :handler (fn [_req]
                          {:status 200
                           :body   {:text "hello world"}})}}]]
      reitit-options)
    (ring/routes
      (ring/create-default-handler))))

(def app-cors (cors/wrap #'app cors-config))

(defn start [port]
  ;; used for repl
  ;; https://stackoverflow.com/questions/17792084/what-is-i-see-in-ring-app
  ;; https://stackoverflow.com/questions/39550513/when-to-use-a-var-instead-of-a-function
  (ah/start-server app-cors {:port port})
  (log/info "API HTTP server runing in port" port))
