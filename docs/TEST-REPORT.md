# ProjectE-JB 1.0.0 测试报告

测试日期：2026-08-01
测试项目：`K:\ideaxiangmu\ProjectE-JB`
服务器镜像：`D:\26.2\26.2`

## 环境

- Java：Oracle/OpenJDK 25.0.2；
- Paper：26.2 build 84 (`26.2-84-26e81c4`)；
- Floodgate-Spigot：2.2.5-SNAPSHOT build 138；
- Geyser：Velocity 端；
- 构建：Maven 3.9.11；
- 数据库：SQLite JDBC 3.50.3.0。

## 自动化测试

执行命令：

```powershell
.\mvnw.cmd clean package
```

结果：8 项测试全部通过。

- 新账户初始余额；
- EMC 增加和条件扣除；
- 单物品学习幂等；
- 批量学习原子提交；
- 余额不足转账不产生部分写入；
- 成功转账和手续费；
- 最大余额溢出回滚；
- 语言及余额数据库重开持久化；
- 操作结果成功/失败分类。

## 最终制品

- 文件：`dist/ProjectE-JB.jar`；
- 大小：14,433,081 字节；
- SHA-256：`3D414C4A51A7E0217013124F23722E75E6069ED42D78B3BEF566A370619FFE82`；
- Java class 主版本：69（Java 25）；
- 已确认 JAR 内含 SQLite JDBC 驱动及服务注册，不包含 Paper API 或 Floodgate API。

## 完整服务器镜像测试

插件成功加载：

```text
ProjectE-JB 1.0.0 enabled for Paper 26.2;
Floodgate=ready-2.2.5-SNAPSHOT (b138-fc99cfc);
EMC values=426
```

已验证：

- `plugin.yml` 被 Paper 正确识别；
- SQLite 原生库可在 Java 25 / Paper 插件类加载器中运行；
- 426 个 EMC Material 均可由 Paper 26.2 解析；
- Floodgate 软依赖加载和 API 初始化正常；
- `/projectejb status` 返回数据库、版本、价格和 Floodgate 状态；
- `/projectejb giveemc`、`takeemc`、`resetemc`、`balance` 数值序列正确；
- `/projectejb reload` 正确重载配置、语言与价格；
- 控制台尝试打开玩家菜单时被正确拒绝；
- 服务器正常 `/stop`，插件关闭并保存 SQLite；
- 第一次启动写入 777 EMC；
- 停服并重新启动后读取仍为 777；
- 测试账户最终重置为 0。

最终交付 JAR 还单独完成了一轮完整启动冒烟测试：服务器到达 `Done`，依次执行 `status`、`balance`、`reload`、`status`，随后正常停服，进程退出码为 0。镜像服内测试文件与 `dist/ProjectE-JB.jar` 的 SHA-256 完全一致。

## 仍需人工确认

这些项目依赖真实客户端交互，无法仅从控制台完整模拟：

1. Java 客户端打开箱子菜单并逐项点击；
2. Java 聊天搜索输入、取消和超时体验；
3. 基岩客户端主表单、搜索、购买和转账按钮；
4. Java 与基岩玩家互相转账时双方提示；
5. 根据服务器经济设计人工复核 EMC 价格平衡。

除上述客户端体验确认外，构建、加载、依赖、数据库和服务器生命周期测试均已完成。
