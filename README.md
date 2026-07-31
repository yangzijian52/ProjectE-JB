# ProjectE-JB

ProjectE-JB 是为 **Paper 26.2** 从零编写的原版物品等价交换插件。Java 玩家使用箱子菜单，Geyser/Floodgate 基岩玩家自动获得表单界面；两端共享同一套 EMC 账户、学习、出售、购买和转账逻辑。

插件不注册伪装物品，不附带资源包、数据包、自定义合成配方或实体转换桌。玩家使用原版客户端即可加入。

## 功能

- 426 种 Paper 26.2 原版物品的可编辑 EMC 默认价格；
- 学习物品、出售主手或批量出售背包；
- 按已学习列表购买，支持分页、搜索和多个数量档位；
- 玩家间 EMC 转账；
- Java 箱子菜单；
- Floodgate 基岩表单，Floodgate 未安装时自动回退为 Java-only；
- 简体中文默认语言和 English 第二语言；
- SQLite WAL 持久化与事务记录；
- 交易前预检、余额上限、重复点击保护、失败回滚；
- 适合通过 QuickMenu 等菜单插件以玩家身份绑定命令。

## 环境要求

- Paper 26.2（使用 build 84 编译和测试）；
- Java 25；
- 基岩表单可选依赖：Floodgate 2.2.5；
- 如果 Geyser 运行在 Velocity，后端 Paper 仍需安装 Floodgate-Spigot。

## 安装

1. 将 `ProjectE-JB.jar` 放入 Paper 主服的 `plugins` 目录。
2. 如需基岩表单，确认主服已安装并正确连接 Floodgate。
3. 启动服务器。配置生成在 `plugins/ProjectE-JB/`。
4. 使用 `/projectejb status` 检查数据库、EMC 价格表和 Floodgate 状态。

## 玩家指令

| 指令 | 说明 |
|---|---|
| `/projectejb menu` | 自动为 Java/基岩玩家打开对应界面 |
| `/projectejb balance` | 查看当前 EMC |
| `/projectejb sell hand` | 出售主手中的整个物品堆 |
| `/projectejb sell inventory` | 出售背包储物格中的合格物品 |
| `/projectejb learn hand` | 消耗主手中的 1 个物品，学习并获得其 EMC |
| `/projectejb learn inventory` | 每种未学习物品消耗 1 个并学习 |
| `/projectejb buy <物品ID> [数量]` | 购买已经学习的物品 |
| `/projectejb pay <在线玩家> <金额>` | 转账 EMC |
| `/projectejb language zh_cn\|en_us` | 切换语言 |
| `/pejb ...` | 主命令简写 |

### QuickMenu 绑定示例

QuickMenu 的 `[command]` 操作使用 `Player#performCommand`，因此以下命令会以点击菜单的玩家身份执行：

```yaml
items:
  projecte:
    slot: 13
    material: EMERALD
    name: "&d等价交换"
    commands:
      - "[command] projectejb menu"

  sell_all:
    slot: 14
    material: HOPPER
    name: "&a批量出售"
    commands:
      - "[command] projectejb sell inventory"
```

不要给普通玩家菜单项使用 `[op]` 或 `[console]`；ProjectE-JB 已提供默认玩家权限。

## 管理员指令

| 指令 | 说明 |
|---|---|
| `/projectejb status` | 查看运行状态 |
| `/projectejb reload` | 重载配置、语言与 EMC 价格 |
| `/projectejb balance <玩家>` | 查看已知玩家余额 |
| `/projectejb setemc <物品ID> <数值>` | 设置价格，`0` 表示禁用 |
| `/projectejb giveemc <玩家> <金额>` | 增加 EMC |
| `/projectejb takeemc <玩家> <金额>` | 扣除 EMC，最低扣到 0 |
| `/projectejb resetemc <玩家>` | 将 EMC 重置为 0 |

对应权限见 [`plugin.yml`](src/main/resources/plugin.yml)。普通玩家权限默认开启，管理员权限默认仅 OP。

## 交易安全规则

默认只接受与新建原版 `ItemStack` 完全一致的物品。以下物品会被拒绝：

- 附魔或改名物品；
- 有耐久损耗的工具和护甲；
- 带有 PDC、CustomModelData 或其他自定义组件的物品；
- 装有内容的容器、收纳袋等；
- 没有在 `emc-values.yml` 中启用价格的物品。

批量出售和学习只处理背包的 36 个储物格，不处理盔甲、副手或光标物品。购买会先检查全部空间和 EMC；任何失败都会取消或回滚交易。

## 配置文件

- `config.yml`：数据库、学习规则、购买上限、转账手续费、菜单数量和 Floodgate 开关；
- `emc-values.yml`：全部可交易原版物品价格；
- `lang/zh_cn.yml`：简体中文；
- `lang/en_us.yml`：English；
- `data.db`：玩家 EMC、语言、学习列表和事务记录。

学习默认消耗一个物品并把同等 EMC 存入玩家账户。可在 `config.yml` 分别关闭消耗或入账，但公开服务器不建议关闭消耗。

## 构建

项目附带 Maven Wrapper：

```powershell
.\mvnw.cmd clean package
```

构建产物位于 `target/ProjectE-JB-1.0.0.jar`。IntelliJ IDEA Community Edition 可直接以 Maven 项目导入。

## 测试状态

- 8 项 JUnit 测试通过；
- 在完整的 Paper 26.2 build 84 镜像中完成加载、Floodgate 检测、价格校验、管理员命令、重载、SQLite 写入及跨两次服务器重启持久化测试；
- Java GUI 点击体验和真人基岩表单点击体验需要发布前人工确认。

完整记录见 [`docs/TEST-REPORT.md`](docs/TEST-REPORT.md)。

## 许可证与致谢

Copyright © 2026 yangzijian52。项目使用 [MIT License](LICENSE)。

本项目的玩法方向受 [Little100/ProjectE-plugin](https://github.com/Little100/ProjectE-plugin) 和 Minecraft ProjectE 模组启发，但 ProjectE-JB 是独立的 clean-room 实现：没有复制其 Java 源码、资源包、数据包或其他 GPL 资产。

第三方依赖说明见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。

---

## English

ProjectE-JB is a clean-room Paper 26.2 EMC exchange plugin for unmodified clients. Java players receive inventory menus, while Floodgate players receive Bedrock forms. It includes learning, selling, buying, player-to-player transfers, SQLite persistence, transaction rollback, Chinese/English messages, and no custom items or resource packs.

Build with `mvnw.cmd clean package`. See the Chinese sections above for the complete command, configuration, safety, and test documentation.
