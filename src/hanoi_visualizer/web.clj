(ns hanoi-visualizer.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.middleware.stacktrace :as trace]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.params :as params]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.basic-authentication :as basic]
            [cemerick.drawbridge :as drawbridge]
            [environ.core :refer [env]]
            [hanoi-visualizer.hanoi :as hanoi]
            [hiccup.core :as hc]
            [hiccup.page :as hp]))

(defn- authenticated? [user pass]
  ;; TODO: heroku config:add REPL_USER=[...] REPL_PASSWORD=[...]
  (= [user pass] [(env :repl-user false) (env :repl-password false)]))

(def ^:private drawbridge
  (-> (drawbridge/ring-handler)
      (session/wrap-session)
      (basic/wrap-basic-authentication authenticated?)))

(def example-req {:ssl-client-cert nil,
                  :remote-addr "0:0:0:0:0:0:0:1",
                  :scheme :http,
                  :query-params {"x" "[(\"a\",\"b\"),(\"a\",\"c\"),(\"b\",\"c\"),(\"a\",\"b\"),(\"c\",\"a\"),(\"c\",\"b\"),(\"a\",\"b\")]"},
                  :form-params {},
                  :request-method :get,
                  :query-string "x=[(%22a%22,%22b%22),(%22a%22,%22c%22),(%22b%22,%22c%22),(%22a%22,%22b%22),(%22c%22,%22a%22),(%22c%22,%22b%22),(%22a%22,%22b%22)]",
                  :route-params {:* ""},
                  :content-type nil,
                  :uri "/",
                  :server-name "localhost",
                  :params {"x" "[(\"a\",\"b\"),(\"a\",\"c\"),(\"b\",\"c\"),(\"a\",\"b\"),(\"c\",\"a\"),(\"c\",\"b\"),(\"a\",\"b\")]", :* ""},
                  :headers {"accept-encoding"
                            "gzip,deflate,sdch",
                            "connection" "keep-alive",
                            "user-agent" "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.146 Safari/537.36",
                            "accept-language" "en-US,en;q=0.8", "accept" "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                            "host" "localhost:5000",
                            "cookie" "wp-settings-time-1=1375675120"},
                  :content-length nil,
                  :server-port 5000,
                  :character-encoding nil,
                  :body 'x })

(defn my-handler [req]
  (let [params (:params req)
        solution-div (try (if params
                            (let [hanoi-input (val (last params))
                                  hanoi-result (read-string hanoi-input)
                                  answer (hanoi/read-fn hanoi-result)
                                  num-rings (hanoi/infer-rings answer)
                                  strings (map (partial hanoi/state->string 3) (hanoi/answer->history answer))]
                              [:table
                               (for [s strings]
                                 [:tr
                                  (for [peg s]
                                    [:td peg])])])
                            [])
                       (catch Exception e [:div "Exception: " (.getMessage e)]))
        example2  "[(\"a\",\"c\"), (\"a\",\"b\"), (\"c\",\"b\")]"
        example3 "[(\"a\",\"b\"),(\"a\",\"c\"),(\"b\",\"c\"),(\"a\",\"b\"),(\"c\",\"a\"),(\"c\",\"b\"),(\"a\",\"b\")]"
        port (:server-port req)
        server (:server-name req)
        url (str server (if port ":" port) port)]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body  (hp/html5 [:head]
                      [:body [:h1 "HELLO!"]
                       [:div "This is a visualizer for solutions in haskel from the yergey lectures "
                        [:a {:href "http://www.seas.upenn.edu/~cis194/lectures/01-intro.html"}
                         "http://www.seas.upenn.edu/~cis194/lectures/01-intro.html"] [:p]
                        "Use the url to pass in your haskell solution as the path or a parameter, e.g.:"]
                       [:div [:a {:href (str url example2)} (str url example2)]]
                       [:div "or " [:a {:href (str url "?x=" example3)} (str url "?=x" example3)]]
                       [:div "Each line is one step in your solution, and it's supposed to be ascii art as though you're looking down on the pegs from above."]
;                       [:div (pr-str :req req)]
;                       [:div (pr-str  hanoi-result ", " (type hanoi-result)(type (first hanoi-result)))]
;                       [:div (pr-str "Rings: " num-rings "," answer)]
;                       [:div (pr-str strings)]
                       solution-div
                       ])}))

(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (slurp (io/resource "500.html"))}))))

(defroutes app
  (ANY "/repl" {:as req}
       (drawbridge req))
  (GET "/*" [req]
       (ring.middleware.params/wrap-params my-handler req))
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn handler [req]
  (let [store (cookie/cookie-store {:key (env :session-secret)})]
    (-> #'app
        ((if (env :production)
           wrap-error-page
           trace/wrap-stacktrace))
        (site {:session {:store store}}))))



(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))
        ;; TODO: heroku config:add SESSION_SECRET=$RANDOM_16_CHARS
        store (cookie/cookie-store {:key (env :session-secret)})]
    (jetty/run-jetty app {:port port})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))
