;; Одноразовая генерация VAPID-ключей (change add-pwa-push, design D5).
;; Печатает пару base64url-строк для GOODMOOD_VAPID_PUBLIC_KEY /
;; GOODMOOD_VAPID_PRIVATE_KEY: открытый ключ — 65-байтовая несжатая точка
;; (её же браузер ждёт в applicationServerKey), приватный — 32 байта d.
;; Запуск: clj -M dev/gen_vapid_keys.clj
(import '[java.security KeyPairGenerator Security]
        '[java.util Base64 Base64$Encoder]
        '[org.bouncycastle.jce ECNamedCurveTable]
        '[org.bouncycastle.jce.provider BouncyCastleProvider]
        '[org.bouncycastle.jce.interfaces ECPrivateKey ECPublicKey])

(Security/addProvider (BouncyCastleProvider.))

(let [spec  (ECNamedCurveTable/getParameterSpec "prime256v1")
      kpg   (doto (KeyPairGenerator/getInstance "ECDH" "BC")
              (.initialize spec))
      kp    (.generateKeyPair kpg)
      b64   (Base64/getUrlEncoder)
      ^ECPublicKey pubk (.getPublic kp)
      pub   (.encodeToString b64 (.getEncoded (.getQ pubk) false))
      ^ECPrivateKey privk (.getPrivate kp)
      ;; d как unsigned 32 байта: toByteArray может дать 33 байта (знак)
      raw   (.toByteArray (.getD privk))
      fixed (byte-array 32)
      _     (System/arraycopy raw 0 fixed (- 32 (alength raw)) (alength raw))
      priv64 (.encodeToString b64 fixed)]
  (println "GOODMOOD_VAPID_PUBLIC_KEY=" pub)
  (println "GOODMOOD_VAPID_PRIVATE_KEY=" priv64))