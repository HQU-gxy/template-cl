(ns tpcl.http
  (:require [aleph.http :as ah]
            [reitit.ring :as ring]
            [simple-cors.aleph.middleware :as cors]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.coercion.spec :as rcs]
            [reitit.ring.coercion :as rrc]
            [muuntaja.core :as m]
            [taoensso.timbre :as log]))

(def cors-config {:cors-config {:allowed-request-methods [:post :get :put :delete]
                                :allowed-request-headers ["Authorization" "Content-Type"]
                                :origins                 "*"}})

;; middles
(def reitit-options
  {:data {:muuntaja m/instance
          :coercion rcs/coercion
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
