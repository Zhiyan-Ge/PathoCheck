-- ============================================================
-- PathoCheck 病理图像质量评估系统 - 建库建表脚本
-- 对应 DatabaseService.java 中的四张业务表
-- 使用方式: mysql -u root -p < schema.sql
-- ============================================================

-- 0. 创建数据库并切换
CREATE DATABASE IF NOT EXISTS pathocheck
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE pathocheck;

-- 1. 数据集记录表
CREATE TABLE IF NOT EXISTS dataset_record (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    dataset_name    VARCHAR(255)    NOT NULL                 COMMENT '数据集名称',
    dataset_path    VARCHAR(500)    NOT NULL                 COMMENT '数据集存储路径',
    image_count     INT             NOT NULL DEFAULT 0       COMMENT '图像总数',
    valid_count     INT             NOT NULL DEFAULT 0       COMMENT '有效图像数',
    invalid_count   INT             NOT NULL DEFAULT 0       COMMENT '无效图像数',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据集记录表';

-- 2. 模型记录表
CREATE TABLE IF NOT EXISTS model_record (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    model_name          VARCHAR(255)    NOT NULL                 COMMENT '模型名称',
    model_path          VARCHAR(500)    NOT NULL                 COMMENT '模型文件路径',
    algorithm           VARCHAR(100)    NOT NULL DEFAULT 'RandomForest' COMMENT '算法名称',
    train_sample_count  INT             NULL                     COMMENT '训练样本数',
    test_sample_count   INT             NULL                     COMMENT '测试样本数',
    accuracy            DOUBLE          NULL                     COMMENT '准确率',
    `precision`         DOUBLE          NULL                     COMMENT '精确率',
    recall              DOUBLE          NULL                     COMMENT '召回率',
    f1_score            DOUBLE          NULL                     COMMENT 'F1分数',
    confusion_matrix    TEXT            NULL                     COMMENT '混淆矩阵(JSON字符串)',
    created_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型记录表';

-- 3. 图像记录表
CREATE TABLE IF NOT EXISTS image_record (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    image_name      VARCHAR(255)    NOT NULL                 COMMENT '图像文件名',
    image_path      VARCHAR(500)    NOT NULL                 COMMENT '图像存储路径',
    file_format     VARCHAR(50)     NULL                     COMMENT '文件格式',
    width           INT             NULL                     COMMENT '图像宽度(像素)',
    height          INT             NULL                     COMMENT '图像高度(像素)',
    file_size       BIGINT          NULL                     COMMENT '文件大小(字节)',
    color_mode      VARCHAR(50)     NULL                     COMMENT '色彩模式',
    created_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图像记录表';

-- 4. 评估报告表
CREATE TABLE IF NOT EXISTS assessment_report (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    image_id            BIGINT          NOT NULL                 COMMENT '关联图像ID',
    image_name          VARCHAR(255)    NULL                     COMMENT '图像名称',
    model_id            BIGINT          NOT NULL                 COMMENT '关联模型ID',
    model_name          VARCHAR(255)    NULL                     COMMENT '模型名称',
    assessment_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评估时间',
    predicted_label     VARCHAR(50)     NULL                     COMMENT '预测标签',
    valid_probability   DOUBLE          NOT NULL DEFAULT 0       COMMENT '有效概率',
    invalid_probability DOUBLE          NOT NULL DEFAULT 0       COMMENT '无效概率',
    mean_red            DOUBLE          NOT NULL DEFAULT 0       COMMENT '平均红色通道值',
    mean_green          DOUBLE          NOT NULL DEFAULT 0       COMMENT '平均绿色通道值',
    mean_blue           DOUBLE          NOT NULL DEFAULT 0       COMMENT '平均蓝色通道值',
    mean_brightness     DOUBLE          NOT NULL DEFAULT 0       COMMENT '平均亮度',
    brightness_std      DOUBLE          NOT NULL DEFAULT 0       COMMENT '亮度标准差',
    mean_saturation     DOUBLE          NOT NULL DEFAULT 0       COMMENT '平均饱和度',
    white_pixel_ratio   DOUBLE          NOT NULL DEFAULT 0       COMMENT '白色像素占比',
    dark_pixel_ratio    DOUBLE          NOT NULL DEFAULT 0       COMMENT '暗像素占比',
    tissue_ratio        DOUBLE          NOT NULL DEFAULT 0       COMMENT '组织区域占比',
    entropy             DOUBLE          NOT NULL DEFAULT 0       COMMENT '信息熵',
    sharpness           DOUBLE          NOT NULL DEFAULT 0       COMMENT '清晰度',
    edge_ratio          DOUBLE          NOT NULL DEFAULT 0       COMMENT '边缘占比',
    quality_score       DOUBLE          NOT NULL DEFAULT 0       COMMENT '综合质量评分',
    final_status        VARCHAR(50)     NULL                     COMMENT '最终状态',
    warning_messages    TEXT            NULL                     COMMENT '警告信息(JSON数组字符串)',
    suggestion          TEXT            NULL                     COMMENT '建议',
    pdf_path            VARCHAR(500)    NULL                     COMMENT 'PDF报告路径',
    PRIMARY KEY (id),
    CONSTRAINT fk_report_image FOREIGN KEY (image_id) REFERENCES image_record (id) ON DELETE CASCADE ,
    CONSTRAINT fk_report_model FOREIGN KEY (model_id) REFERENCES model_record (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估报告表';
