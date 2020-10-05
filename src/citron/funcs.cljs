(ns citron.funcs
  (:require
   [citron.db :as db]
   [citron.http :as http]
   [citron.utils :as utils]
   [accountant.core :as a]
   [clojure.string :as s]))

(def app-title "Citron")

(def page-size 100)

(defn parent [path]
  (s/join "/" (butlast (s/split path #"/"))))

(defn top? [path]
  (#{"." "./" ""} path))

(defn set-login-value [key]
  (fn [e]
    (swap! db/login-store assoc key (-> e .-target .-value))))

(defn set-app-state-value
  ([key]
   (set-app-state-value key nil))
  ([key on-done]
   (fn [e]
     (swap! db/app-state assoc key (-> e .-target .-value))
     (when on-done
       (on-done)))))

(defn to-display-path
  ([parent path]
   (subs path (inc (count parent))))
  ([path]
   (s/replace (or path "") #"^\./" "")))

(defn login []
  (when-not (some s/blank? (vals @db/login-store))
    (http/login @db/login-store)))

(defn goto-file [{:keys [path]}]
  (http/get-file path))

(defn goto-parent [{:keys [path]}]
  (goto-file {:path (parent path)}))

(defn cancel-add-msg []
  (swap! db/app-state dissoc :hide-msg-content :new-msg)
  (reset! db/temp-msg-store ""))

(defn edit-msg-board []
  (let [{:keys [new-msg]} (swap! db/app-state update :new-msg not)]
    (if new-msg
      (swap! db/app-state assoc :hide-msg-content nil)
      (when-not (s/blank? @db/temp-msg-store)
        (http/put-msg-board @db/temp-msg-store)))))

(defn del-msg-board []
  (swap! db/app-state update :del-msg not))

(defn toggle-hidden-msg-content []
  (when-not (:new-msg @db/app-state)
    (swap! db/app-state update :hide-msg-content not)))

(defn toggle-rename-file [filename]
  (fn [_]
    (let [{:keys [rename-file?]} (swap! db/app-state update :rename-file? not)]
      (swap! db/app-state assoc :filename (when rename-file? filename)))))

(defn toggle-file-filter [_]
  (let [{:keys [filter?]} (swap! db/app-state update :filter? not)]
    (when-not filter?
      (a/navigate! "#/user" {:path (:path @db/file-store)
                             :offset 0}))))

;; play all music inside current idrectory
(defn add-to-playlist []
  (swap! db/audioplayer-state update :playlist concat (filter utils/music? (:files @db/file-store))))

(defn save-filename [path]
  (fn [_]
    (http/rename-file path (:filename @db/app-state))))

