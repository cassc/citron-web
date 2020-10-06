(ns citron.funcs
  (:require
   [citron.db :as db]
   [citron.http :as http]
   [citron.utils :as utils]
   
   [goog.object :as go]
   [taoensso.timbre :as t]
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
  (swap! db/audioplayer-state update :playlist concat (filter utils/music? (:files @db/file-store)))
  (db/set-error "Success!" 1000 :info))

(defn make-static-path [path]
  (str "/staticfile/" path))

(defn current-track-path [id]
  (some-> (:playlist @db/audioplayer-state)
          (nth id nil)
          (:path)
          (make-static-path)))

(defn current-track-name [id]
  (some-> (current-track-path id)
          (utils/to-filename)))

(defn save-filename [path]
  (fn [_]
    (http/rename-file path (:filename @db/app-state))))


(defn toggle-floating-menu []
  (swap! db/floatingmenu-state update :show? not))

(defn num-tracks []
  (count (:playlist @db/audioplayer-state)))

(defn toggle-audio-shuffle []
  (swap! db/audioplayer-state update :shuffle? not))

(defn audio-play!
  ([]
   (audio-play! false nil))
  ([resume?]
   (audio-play! resume? nil))
  ([resume? track]
   (let [player (utils/get-element-by-id db/hidden-audio-player-id)]
     (if (and resume?
              (pos? (:current-time @db/audioplayer-state 0))
              (not (s/ends-with? (go/get player "src") "html")))
       (.play (utils/get-element-by-id db/hidden-audio-player-id))
       (let [track (or track (current-track-path (:id @db/audioplayer-state)))]
         (.pause player)
         (go/set player "src" track)
         (.load player)
         (go/set player "currentTime" 0)
         (.play player))))))

(defn resume-or-play! []
  (audio-play! true))

(defn play-next-track []
  (when (pos? (num-tracks))
    (let [prev-id (:id @db/audioplayer-state)
          n-tracks (num-tracks)
          next-id (if (:shuffle? @db/audioplayer-state)
                    (rand-int n-tracks)
                    (let [id (inc prev-id)]
                      (if (>= id n-tracks)
                        0
                        id)))]
      (swap! db/audioplayer-state assoc :id next-id :prev-id prev-id)
      (audio-play!))))

(defn play-prev-track []
  (when (pos? (num-tracks))
    (let [{:keys [id prev-id]} @db/audioplayer-state]
      (swap! db/audioplayer-state assoc :id prev-id :prev-id (if (:shuffle? @db/audioplayer-state)
                                                               (rand-int (num-tracks))
                                                               (max 0 (dec prev-id))))
      (audio-play!))))

(defn stop-audio-play []
  (let [player (utils/get-element-by-id db/hidden-audio-player-id)]
    (try
      (.pause player) 
      (catch :default e
        (t/error "Audio pause error:" e)))))

(defn toggle-audio-play []
  (when (pos? (num-tracks))
    (let [{:keys [paused?]} (swap! db/audioplayer-state update :paused? not)]
      (if paused?
        (stop-audio-play)
        (resume-or-play!)))))


(defn update-music-meta! []
  (let [player (utils/get-element-by-id db/hidden-audio-player-id)
        volume (go/get player "volume")
        duration (go/get player "duration")
        current-time (go/get player "currentTime")]
    (t/info "get player v/d/c" volume duration current-time)
    (swap! db/audioplayer-state assoc :volume volume)
    (swap! db/audioplayer-state assoc :duration duration)
    (swap! db/audioplayer-state assoc :current-time current-time)))

(defn reg-music-player-events! []
  (let [player (utils/get-element-by-id db/hidden-audio-player-id)]
    (set! (.-onloadedmetadata player) update-music-meta!)
    (set! (.-onplay player) (fn []
                              (swap! db/audioplayer-state assoc :paused? false)))
    (set! (.-onplaying player) (fn []
                                 (swap! db/audioplayer-state assoc :paused? false)))
    (set! (.-ontimeupdate player)
          (fn []
            (swap! db/audioplayer-state assoc :current-time (go/get player "currentTime"))))
    (set! (.-onpause player) (fn [] (swap! db/audioplayer-state assoc  :paused? true)))
    (set! (.-onended player) (fn []
                               (t/info "play end")
                               (play-next-track)))
    (set! (.-onerror player) (fn []
                               (t/warn "Ignore player error" (.-code (.-error player)))))
    (set! (.-oncanplay player) (fn []
                                 (t/debug "music canplay")))
    (set! (.-oncanplaythrough player) (fn []
                                        (t/debug "music canplaythrough")))))

(defn init-audio-src! []
  (let [player (utils/get-element-by-id db/hidden-audio-player-id)]
    (swap! db/audioplayer-state assoc :paused? true)
    (when-let [track (current-track-path (:id @db/audioplayer-state))]
      (t/info "init-audio-src")
      (go/set player "src" track))))
