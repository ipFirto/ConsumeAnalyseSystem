-- Enterprise schema for consume data analytics system
-- Date: 2026-02-20

CREATE DATABASE IF NOT EXISTS `consume_enterprise`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `consume_enterprise`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `order_operate_log`;
DROP TABLE IF EXISTS `payment_record`;
DROP TABLE IF EXISTS `consumption_order`;
DROP TABLE IF EXISTS `cart_item`;
DROP TABLE IF EXISTS `product`;
DROP TABLE IF EXISTS `city`;
DROP TABLE IF EXISTS `province`;
DROP TABLE IF EXISTS `platform`;
DROP TABLE IF EXISTS `user`;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `user` (
  `user_id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `user_email` varchar(128) NOT NULL COMMENT 'Unique email',
  `user_name` varchar(32) NOT NULL COMMENT 'Display name',
  `password_hash` varchar(128) NOT NULL COMMENT 'Password hash',
  `phone` varchar(20) DEFAULT NULL COMMENT 'Phone',
  `register_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Register time',
  `last_login_time` datetime DEFAULT NULL COMMENT 'Last login time',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1-active, 0-disabled',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_user_email` (`user_email`),
  KEY `idx_register_time` (`register_time`),
  KEY `idx_user_status_userid` (`status`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `platform` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(20) NOT NULL COMMENT 'douyin/tmall/jd/pdd/dewu',
  `name` varchar(20) NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_code` (`code`),
  UNIQUE KEY `uk_platform_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `province` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(6) NOT NULL COMMENT 'Administrative code',
  `name` varchar(50) NOT NULL COMMENT 'Province name',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '1 province level',
  `type` tinyint NOT NULL DEFAULT '1' COMMENT '1 province 2 municipality 3 autonomous region 4 SAR',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 active 0 disabled',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_province_code` (`code`),
  UNIQUE KEY `uk_province_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `city` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `province_id` bigint NOT NULL COMMENT 'Belonging province id',
  `code` varchar(6) NOT NULL COMMENT 'Administrative code',
  `name` varchar(50) NOT NULL COMMENT 'City name',
  `postcode` varchar(10) DEFAULT NULL COMMENT 'Post code',
  `level` tinyint NOT NULL DEFAULT '2' COMMENT '2 city level',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 active 0 disabled',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_city_code` (`code`),
  UNIQUE KEY `uk_city_name_province` (`province_id`,`name`),
  KEY `idx_city_province` (`province_id`),
  CONSTRAINT `fk_city_province` FOREIGN KEY (`province_id`) REFERENCES `province` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `platform_id` bigint NOT NULL COMMENT 'Platform id',
  `brand` varchar(100) DEFAULT NULL COMMENT 'Brand',
  `product_name` varchar(255) NOT NULL COMMENT 'Product name',
  `category` varchar(100) DEFAULT NULL COMMENT 'Category',
  `amount` decimal(12,2) NOT NULL DEFAULT '999.00' COMMENT 'Current listed amount',
  `stock` int NOT NULL DEFAULT '0' COMMENT 'Remaining stock',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 active 0 disabled',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_brand_name_cat` (`platform_id`,`brand`,`product_name`,`category`),
  KEY `idx_product_platform` (`platform_id`),
  KEY `idx_product_category` (`category`),
  KEY `idx_product_name` (`product_name`),
  KEY `idx_product_platform_status_created_id` (`platform_id`,`status`,`created_at`,`id`),
  CONSTRAINT `fk_product_platform` FOREIGN KEY (`platform_id`) REFERENCES `platform` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cart_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int unsigned NOT NULL,
  `product_id` bigint NOT NULL,
  `city_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Unit amount snapshot',
  `quantity` int NOT NULL DEFAULT '1',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 valid 0 deleted',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product_city` (`user_id`,`product_id`,`city_id`),
  KEY `idx_cart_user_time` (`user_id`,`updated_at`),
  KEY `idx_cart_user_status` (`user_id`,`status`),
  CONSTRAINT `fk_cart_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_cart_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_cart_city` FOREIGN KEY (`city_id`) REFERENCES `city` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `consumption_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int unsigned NOT NULL COMMENT 'User id',
  `order_no` varchar(64) NOT NULL COMMENT 'Business order no',
  `product_id` bigint NOT NULL COMMENT 'Product id',
  `city_id` bigint NOT NULL COMMENT 'City id',
  `quantity` int NOT NULL DEFAULT '1' COMMENT 'Order quantity',
  `amount` decimal(12,2) NOT NULL COMMENT 'Order amount',
  `remark` varchar(500) DEFAULT NULL COMMENT 'Order remark',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 unpaid 2 paid 3 timeout 4 canceled',
  `pay_deadline` datetime NOT NULL COMMENT 'Payment deadline',
  `pay_time` datetime DEFAULT NULL COMMENT 'Payment success time',
  `payment_method` varchar(32) DEFAULT NULL COMMENT 'Payment method',
  `payment_no` varchar(64) DEFAULT NULL COMMENT 'Payment serial no',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Order create time',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_user_order_no` (`user_id`,`order_no`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  KEY `idx_order_user_created` (`user_id`,`created_at`),
  KEY `idx_order_status_deadline` (`status`,`pay_deadline`),
  KEY `idx_order_city_created` (`city_id`,`created_at`),
  KEY `idx_order_product_created` (`product_id`,`created_at`),
  KEY `idx_order_status_user` (`status`,`user_id`),
  CONSTRAINT `fk_order_city` FOREIGN KEY (`city_id`) REFERENCES `city` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_order_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `payment_no` varchar(64) NOT NULL COMMENT 'Payment serial no',
  `order_no` varchar(64) NOT NULL COMMENT 'Business order no',
  `user_id` int unsigned NOT NULL COMMENT 'User id',
  `pay_amount` decimal(12,2) NOT NULL COMMENT 'Paid amount',
  `pay_channel` varchar(32) NOT NULL DEFAULT 'MOCK' COMMENT 'Payment channel',
  `pay_status` tinyint NOT NULL DEFAULT '1' COMMENT '1 success 2 failed 3 refund',
  `paid_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `request_payload` varchar(500) DEFAULT NULL,
  `response_payload` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_record_no` (`payment_no`),
  KEY `idx_payment_order` (`order_no`),
  KEY `idx_payment_user` (`user_id`),
  CONSTRAINT `fk_payment_order` FOREIGN KEY (`order_no`) REFERENCES `consumption_order` (`order_no`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_payment_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_operate_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL,
  `user_id` int unsigned NOT NULL,
  `operation` varchar(64) NOT NULL COMMENT 'CREATE_ORDER/PAY_ORDER/TIMEOUT_CLOSE/OTHER',
  `from_status` tinyint DEFAULT NULL,
  `to_status` tinyint DEFAULT NULL,
  `operator_type` varchar(32) NOT NULL DEFAULT 'SYSTEM' COMMENT 'USER/SYSTEM/JOB',
  `operator_id` varchar(64) DEFAULT NULL COMMENT 'Operator id if any',
  `detail` varchar(1000) DEFAULT NULL COMMENT 'Operation details',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_log_order_time` (`order_no`,`created_at`),
  KEY `idx_order_log_user_time` (`user_id`,`created_at`),
  CONSTRAINT `fk_order_log_order` FOREIGN KEY (`order_no`) REFERENCES `consumption_order` (`order_no`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_order_log_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `platform` (`id`, `code`, `name`, `status`)
VALUES
  (1, 'douyin', '抖音', 1),
  (2, 'tmall',  '天猫', 1),
  (3, 'jd',     '京东', 1),
  (4, 'pdd',    '拼多多', 1),
  (5, 'dewu',   '得物', 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `status` = VALUES(`status`);
