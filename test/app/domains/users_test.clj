(ns app.domains.users-test
  (:require [app.domains.users :as users]
            [clojure.string :as str]
            [app.test-helpers :as test-helpers]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private ds-atom (atom nil))

(use-fixtures :each #(test-helpers/with-test-db :users ds-atom %))

(deftest authenticate-roundtrip
  (testing "create-user stores a bcrypt hash and authenticate matches the password"
    (let [user (users/create-user @ds-atom {:email "a@b.c"
                                            :password "secret123"
                                            :display-name "A"})]
      (is (nil? (:password-hash user)))
      (is (= "user" (:role user)))
      (is (= "a@b.c" (:email user)))
      (let [auth (users/authenticate @ds-atom "a@b.c" "secret123")]
        (is (= (:id user) (:id auth)))
        (is (= "A" (:display-name auth)))
        (is (nil? (:password-hash auth)))))))

(deftest authenticate-wrong-password-returns-nil
  (testing "wrong password does not authenticate"
    (users/create-user @ds-atom {:email "a@b.c"
                                 :password "secret123"
                                 :display-name "A"})
    (is (nil? (users/authenticate @ds-atom "a@b.c" "wrong")))))

(deftest authenticate-unknown-email-returns-nil
  (testing "unknown email does not authenticate"
    (is (nil? (users/authenticate @ds-atom "nobody@b.c" "secret123")))))

(deftest password-stored-as-bcrypt-hash
  (testing "password_hash is a hashed string (never plaintext)"
    (let [user (users/create-user @ds-atom {:email "a@b.c"
                                            :password "secret123"
                                            :display-name "A"})
          stored (jdbc/execute-one!
                  @ds-atom
                  ["SELECT password_hash FROM users WHERE id = ?" (:id user)]
                  {:builder-fn rs/as-unqualified-kebab-maps})]
      (is (str/includes? (:password-hash stored) "$")
          (str "hash format marker expected: " (:password-hash stored)))
      (is (not (str/includes? (:password-hash stored) "secret123"))))))

(deftest email-uniqueness-enforced
  (testing "second user with the same email is rejected"
    (users/create-user @ds-atom {:email "a@b.c"
                                 :password "secret123"
                                 :display-name "A"})
    (is (thrown? java.sql.SQLException
                 (users/create-user @ds-atom {:email "a@b.c"
                                              :password "other123"
                                              :display-name "B"})))))

(deftest register-user-validation
  (testing "register-user rejects blank email and short passwords"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Email"
                          (users/register-user @ds-atom {:email ""
                                                         :password "secret123"
                                                         :display-name "A"})))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Password"
                          (users/register-user @ds-atom {:email "a@b.c"
                                                         :password "short"
                                                         :display-name "A"})))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Display name"
                          (users/register-user @ds-atom {:email "a@b.c"
                                                         :password "secret123"})))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"already taken"
                          (do (users/create-user @ds-atom {:email "a@b.c"
                                                           :password "secret123"
                                                           :display-name "A"})
                              (users/register-user @ds-atom {:email "a@b.c"
                                                             :password "secret1234"
                                                             :display-name "B"}))))))

(deftest register-user-creates-default-role
  (testing "register-user creates a user with role user"
    (let [user (users/register-user @ds-atom {:email "r@b.c"
                                              :password "secret1234"
                                              :display-name "R"})]
      (is (= "user" (:role user)))
      (is (= "r@b.c" (:email user))))))