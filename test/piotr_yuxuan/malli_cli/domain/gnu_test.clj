(ns piotr-yuxuan.malli-cli.domain.gnu-test
  (:require [piotr-yuxuan.malli-cli.domain.gnu :as gnu]
            [clojure.test :refer [deftest testing is]]))

(deftest long-option?-test
  (testing "Argument"
    (is (not (gnu/long-option? "a"))))
  (testing "Short options"
    (is (not (gnu/long-option? "-a")))
    (is (not (gnu/long-option? "--a"))))
  (testing "Long options"
    (is (gnu/long-option? "--a-"))
    (is (gnu/long-option? "--ab"))
    (is (gnu/long-option? "--a-b")))
  (testing "Long option with value"
    (is (gnu/long-option? "--a-b=c"))))

(deftest long-option-with-value?-test
  (testing "Argument"
    (is (not (gnu/long-option-with-value? "a"))))
  (testing "Short options"
    (is (not (gnu/long-option-with-value? "-a"))))
  (is (not (gnu/long-option-with-value? "--a")))
  (testing "Long options without values"
    (is (not (gnu/long-option-with-value? "--a-")))
    (is (not (gnu/long-option-with-value? "--éé")))
    (is (not (gnu/long-option-with-value? "--a-b")))
    (is (not (gnu/long-option-with-value? "--a-é"))))
  (testing "Long options with values"
    (is (gnu/long-option-with-value? "--a-b=c"))
    (is (gnu/long-option-with-value? "--a-b=é"))))

(deftest long-option-without-value?-test
  (testing "Argument"
    (is (not (gnu/long-option-without-value? "a"))))
  (testing "Short options"
    (is (not (gnu/long-option-without-value? "-a"))))
  (is (not (gnu/long-option-without-value? "--a")))
  (testing "Long options without values"
    (is (gnu/long-option-without-value? "--a-"))
    (is (not (gnu/long-option-without-value? "--éé")))
    (is (gnu/long-option-without-value? "--a-b"))
    (is (not (gnu/long-option-without-value? "--a-é"))))
  (testing "Long options with values"
    (is (not (gnu/long-option-without-value? "--a-b=c")))
    (is (not (gnu/long-option-without-value? "--a-b=é")))))
