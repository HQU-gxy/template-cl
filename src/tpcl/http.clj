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
            [clojure.string :as str]
            [taoensso.timbre :as log])
  (:use [tpcl.formatter :only [format]]))

(def template-path "templates")

(defn normalize-path
  [path]
  (let [p (if (string? path) path (str path))]
    (str/replace p "\\" "/")))

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

(defn append-extension
  "`name` is a string of stem (filename without dot) of a file or a full filename.
  `extension` is a string of extension (without dot)"
  [parent name extension]
  (let [dot-extension (if (str/starts-with? extension ".")
                        extension
                        (str "." extension))
        file-temp-path (fs/path parent name)
        ext (fs/extension file-temp-path)
        file-path (if (empty? ext)
                    (let [parent (fs/parent file-temp-path)
                          filename (fs/file-name file-temp-path)]
                      (fs/path parent (str filename dot-extension)))
                    file-temp-path)]
    file-path))

(def to-swagger
  {:get {:handler
         (fn [_req] {:status  302
                     :headers {"location" "/swagger/index.html"}})}})

(def app
  "router handler"
  (ring/ring-handler
    (ring/router
      [["" {:no-doc true}
        ["/swagger.json" {:get (swagger/create-swagger-handler)}]
        ["/swagger" to-swagger]
        ["/swagger/" to-swagger]
        ["/swagger/*" {:get (swagger-ui/create-swagger-ui-handler)}]]
       ["/template"
        ["/" {:get {:summary "get templates"
                    :handler (fn [_req]
                               (let [files (fs/list-dir template-path)
                                     files (filter #(= (fs/extension %) "json") files)
                                     ;; remove extension
                                     files (map #(first (fs/split-ext (fs/file-name %))) files)]
                                 {:status 200
                                  :body   {:files files}}))}}]
        ["/:name" {:get    {:summary    "get a template"
                            :parameters {:path {:name string?}}
                            :handler    (fn [req]
                                          (let [{{{:keys [name]} :path} :parameters} req
                                                file-path (append-extension template-path name "json")
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
                                                                  :error (format "template not found for file '{}'" (normalize-path file-path))})
                                                               {:code  400
                                                                :error (format "'{}' doesn't have a valid template extension" name)})
                                                code (:code file-content)
                                                content (:content file-content)
                                                error (:error file-content)]
                                            (if (nil? error)
                                              {:status code
                                               :body   content}
                                              {:status code
                                               :body   {:error error}})))}
                   :post   {:summary    "create a template"
                            :parameters {:path {:name string?} :body map?}
                            :handler    (fn [req]
                                          (let [{{{:keys [name]} :path} :parameters} req
                                                ;; content is unmarshalled from json automatically by middleware
                                                {{content :body} :parameters} req
                                                file-path (append-extension template-path name "json")
                                                result (if (= (fs/extension file-path) "json")
                                                         (if-not (fs/exists? file-path)
                                                           (try (do
                                                                  (spit (str file-path) (json/encode content))
                                                                  {:code 200})
                                                                (catch Exception e
                                                                  {:code  500
                                                                   :error (.getMessage e)}))
                                                           {:code  400
                                                            :error (format "file '{}' already exists" (normalize-path file-path))})
                                                         {:code  400
                                                          :error (format "'{}' doesn't have a valid template extension" name)})
                                                code (:code result)
                                                error (:error result)]
                                            (if (nil? error)
                                              {:status code}
                                              {:status code
                                               :body   {:error error}})))}
                   :delete {:summary    "delete a template"
                            :parameters {:path {:name string?}}
                            :handler    (fn [req]
                                          (let [{{{:keys [name]} :path} :parameters} req
                                                file-path (append-extension template-path name "json")
                                                result (if (= (fs/extension file-path) "json")
                                                         (if (fs/exists? file-path)
                                                           (try (do
                                                                  (fs/delete-if-exists file-path)
                                                                  {:code 200})
                                                                (catch Exception e
                                                                  {:code  500
                                                                   :error (.getMessage e)}))
                                                           {:code  400
                                                            :error (format "file '{}' does not existed" (normalize-path file-path))})
                                                         {:code  400
                                                          :error (format "'{}' doesn't have a valid template extension" name)})
                                                code (:code result)
                                                error (:error result)]
                                            (if (nil? error)
                                              {:status code}
                                              {:status code
                                               :body   {:error error}})))}}]]
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
