(ns piotr-yuxuan.malli-cli.domain.posix-test
  (:require [clojure.test :refer [deftest testing is]]
            [piotr-yuxuan.malli-cli.domain.posix :as posix]))

(deftest option?-test
  (testing "Arguments"
    (is (not (posix/option? "a-a")))
    (is (not (posix/option? "a"))))
  (is (posix/option? "-a"))
  (is (posix/option? "-ab"))
  (testing "Option - with and without value"
    (is (posix/option? "-"))
    (is (posix/option? "-é"))
    (is (posix/option? "-a-"))
    (is (posix/option? "-é-"))
    (is (posix/option? "-a-a"))
    (is (posix/option? "-abc"))
    (is (posix/option? "-é-a"))
    (is (posix/option? "--a"))))

(deftest single-option?-test
  (testing "Arguments"
    (is (not (posix/single-option? "a-a")))
    (is (not (posix/single-option? "a"))))
  (is (posix/single-option? "-a"))
  (testing "Option a with argument b"
    (is (posix/single-option? "-ab")))
  (testing "Option - with and without value"
    (is (posix/single-option? "-"))
    (is (posix/single-option? "-é"))
    (is (posix/single-option? "-a-"))
    (is (posix/single-option? "-é-"))
    (is (posix/single-option? "-a-a"))
    (is (posix/single-option? "-abc"))
    (is (posix/single-option? "-é-a"))
    (is (posix/single-option? "--a"))))

(deftest single-option-without-value?-test
  (testing "Arguments"
    (is (not (posix/single-option-without-value? "a-a")))
    (is (not (posix/single-option-without-value? "a"))))
  (is (posix/single-option-without-value? "-a"))
  (is (not (posix/single-option-without-value? "-ab")))
  (testing "Option - with and without value"
    (is (posix/single-option-without-value? "-"))
    (is (not (posix/single-option-without-value? "-é")))
    (is (not (posix/single-option-without-value? "-a-")))
    (is (not (posix/single-option-without-value? "-é-")))
    (is (not (posix/single-option-without-value? "-a-a")))
    (is (not (posix/single-option-without-value? "-abc")))
    (is (not (posix/single-option-without-value? "-é-a")))
    (is (not (posix/single-option-without-value? "--a")))))

(deftest grouped-options?-test
  (testing "Arguments"
    (is (not (posix/grouped-options? "a-a")))
    (is (not (posix/grouped-options? "a"))))
  (testing "A single option is not grouepd with itself"
    (is (not (posix/grouped-options? "-a"))))
  (is (posix/grouped-options? "-ab"))
  (testing "Grouped options takes values at the end"
    (is (posix/grouped-options? "-abé"))
    (is (not (posix/grouped-options? "-é"))))
  (testing "Option - with and without value"
    (is (not (posix/grouped-options? "-")))
    (is (not (posix/grouped-options? "-é")))
    (is (not (posix/grouped-options? "-a-")))
    (is (not (posix/grouped-options? "-é-")))
    (is (not (posix/grouped-options? "-a-a")))
    (is (not (posix/grouped-options? "-é-a")))
    (is (not (posix/grouped-options? "--a")))))
