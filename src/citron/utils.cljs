(ns citron.utils)

(defn set-hash! [loc]
  (set! (.-hash js/window.location) loc))

