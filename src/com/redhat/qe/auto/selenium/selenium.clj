(ns com.redhat.qe.auto.selenium.selenium
  (:import [com.redhat.qe.auto.selenium ExtendedSelenium Element LocatorTemplate]))

(defprotocol SeleniumLocatable
  (sel-locator [x]))

(declare ^:dynamic sel)
(def jquery-ajax-finished (ExtendedSelenium/JQUERY_AJAX_FINISHED_CONDITION))
(def prototype-ajax-finished (ExtendedSelenium/PROTOTYPE_AJAX_FINISHED_CONDITION))
(def dojo-ajax-finished (ExtendedSelenium/DOJO_AJAX_FINISHED_CONDITION))

(defn new-sel [host port browser-type url]
  (ExtendedSelenium. host port browser-type url))

(defn connect "Create a new selenium instance." [host port browser-type url]
  (def ^:dynamic sel (new-sel host port browser-type url)))

(defn new-element [locator-strategy & args]
  (Element. locator-strategy (into-array args)))

(defn locator-args
  "If any args are keywords, look them up via
SeleniumLocatable protocol (which should return a selenium String
locator). Returns the args list with those Strings in place of the
keywords."
  [& args]
  (for [arg args]
    (if (keyword? arg) 
      (or (sel-locator arg)
          (throw (IllegalArgumentException.
                  (str "Locator " arg " not found in UI mapping."))))
      arg)))

(defn call-sel [action & args]
  (clojure.lang.Reflector/invokeInstanceMethod
    sel action (into-array Object (apply locator-args args))))

(defmacro browser
  "Call method 'action' on selenium, with the given args - keywords
will be looked up and converted to String locators (see locator-args)"
  [action & args]
  `(call-sel ~(str action) ~@args))

(defmacro ->browser "Performs a series of actions using the browser"
  [ & forms]
  `(do ~@(for [form forms] `(browser ~@form))))

(def no-wait (constantly nil))

(defn load-wait []
  (browser waitForPageToLoad "60000"))

(defn fill-item
  "If el is a function, assume vals are args, and call el with args. Otherwise,
   el is an element, and it's filled in with val depending on what
   type it is."
  [el val]
  (if (fn? el)
    (apply el val)
    (let [eltype (browser getElementType el)]
      (cond (= eltype "selectlist") (browser select el val)
            (= eltype "checkbox") (browser checkUncheck el (boolean val))
            :else (browser setText el val)))))

(defn fill-form
  "Fills in a standard HTML form. items is a mapping of locators of
   form elements, to the string values that should be selected or
   entered. You can also map function names to arglists, to perform
   other tasks while filling in the form. If you care about the order
   the items are filled in, use a list instead of a map. 'submit' is a
   locator for the submit button to click at the end. Optional no-arg
   fn argument post-fn will be called after the submit click.
   Example:
    (fill-form [:user 'joe' :password 'blow' choose-type ['manager']] :submit)"
  [items submit & [post-fn]]
  (let [ordered-items (if (sequential? items)
                        (partition 2 items)
                        (into [] items))
        filtered (filter #(not= nil (second %)) ordered-items )]
    (when (-> filtered count (> 0))
      (doseq [[el val] ordered-items]
        (fill-item el val))
      (browser click submit)
      ((or post-fn load-wait)))
    filtered))

(defmacro loop-with-timeout [timeout bindings & forms]
  `(let [starttime# (System/currentTimeMillis)]
     (loop ~bindings
       (if  (> (- (System/currentTimeMillis) starttime#) ~timeout)
	 (throw (RuntimeException. (str "Hit timeout of " ~timeout "ms.")))
	 (do ~@forms)))))

(defn- first-appear [sel-fn timeout & elements]
  (loop-with-timeout timeout []
    (or (some #(if (call-sel sel-fn %1) %1) elements)
        (do (Thread/sleep 1000)
            (recur)))))

(defn first-present [timeout & elements]
  (apply first-appear "isElementPresent" timeout elements))

(defn first-visible [timeout & elements]
  (apply first-appear "isVisible" timeout elements))

(defmethod print-method Element [e out]
  (print-ctor e (fn [o w] (print-method (.getLocator o) w)) out))