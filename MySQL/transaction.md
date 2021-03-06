# MySQL事务

数据库事务( transaction)是访问并可能操作各种数据项的一个数据库操作序列，这些操作要么全部执行,要么全部不执行，是一个不可分割的工作单位。事务由事务开始与事务结束之间执行的全部数据库操作组成。



## 事务的四大特性

* A（Atomicity）原子性：事务中的全部操作在数据库中是不可分割的，要么全部完成，要么全部不执行 
* C（Consistency）一致性：几个并行执行的事务，其执行结果必须与按某一顺序串行执行的结果相一致
* I（Isolation）独立性：事务的执行不受其他事务的干扰，事务执行的中间结果对其他事务必须是透明的 
* D（Durability）持久性：对于任意已提交事务，系统必须保证该事务对数据库的改变不被丢失，即使数据库出现故障



## 并发事务处理带来的问题

* **更新丢失（Lost Update）**：当两个或多个事务选择同一行，然后基于最初选定的值更新该行的时候，由于每个事务都不知道其他事务的存在，会发生更新丢失的问题

  > 例如两个程序员修改同一个文件，每个程序员独立更改文件的副本，然后保存更改的副本，覆盖原始文档，最后另外一个程序员又去覆盖前一个程序员修改好的文档

* **脏读（Dirty Reads）**：一个事务正在对一条记录做修改，这个事务完成提交前，这条数据就处于不一致状态；这时，另外一个事务也来读取同一行记录，如果不加控制，第二个事务读取了这些"脏"数据，并据此做进一步处理，就会产生未提交的数据依赖关系，这种现象就做“脏读”

  > 简单来说就是事务A读到事务B已修改但尚未提交的数据，还在这个数据的基础上做了操作。此时，如果事务B回滚，A读取的数据无效，不符合一致性要求

* **不可重复读（Non-Repeatable Reads）**：一个事务在读取某些数据后的某个时间，再次读取以前读取过的数据，却发现读出的数据已经发生改变，或某些记录已经删除了！种种现象叫做“不可重复读”

  > 简单来说就是事务A读取到了事务B已经提交的修改数据，不符合隔离性

* **幻读（Phantom Reads）**：一个事务按照相同的查询条件读取以前检索过的数据，却发现了其他事务插入了满足条件的新数据，这种现象称作"幻读"

  > 简单来说就是事务A读取到了事务B提交的新增数据，不符合隔离性
  >
  > 幻读和脏读类似：
  >
  > * 脏读是事务B里面修改了数据
  > * 幻读是事务B里面新增了数据



## 银行转账例子

A的银行账户有$1000，B的账户有$800，此时A向B转$200

这个转账动作有两个操作，从A的账户中减少$200，在B的账户中加$200，这连个操作是不可以在分割的，所以这连个操作必须要们全部成功，要么全部失败，这就是原子性

转账完成前和后A和B的账户的总额不会改变，这就是一致性

如果A和C都分别向B转$200，那这就是两个事务，然而这两个事务时不会互相干扰的最终B的账户会多出$400，不会因为同时转账而导致数目不对，这就是独立性

一旦转账成功，那么账户信息就是被永久储存的，这就是持久性



## MySQL操作

```sql
mysql> SELECT * FROM `user`;
+----+------+---------+
| id | name | money   |
+----+------+---------+
|  1 | A    | 1000.00 |
|  2 | B    |  800.00 |
|  3 | C    | 1200.00 |
+----+------+---------+
3 rows in set (0.00 sec)

```

MySQL中每执行一条语句其实都是一个事务，但是其中不需要我们手动提交，因为` AUTOCOMMIT `变量为`1`（表示自动提交事务），要使用事务首先要关闭自动提交（设置为`0`），下面是A转账B的例子

```sql
mysql> SET AUTOCOMMIT=0;
Query OK, 0 rows affected (0.00 sec)

mysql> BEGIN;
Query OK, 0 rows affected (0.00 sec)

mysql> UPDATE `user` SET money=money-200 WHERE name='A';
Query OK, 1 row affected (0.00 sec)
Rows matched: 1  Changed: 1  Warnings: 0

mysql> UPDATE `user` SET money=money+200 WHERE name='B';
Query OK, 1 row affected (0.00 sec)
Rows matched: 1  Changed: 1  Warnings: 0

```

`SET AUTOCOMMIT=0`用来关闭事务自动提交

`BEGIN`用于开启一个事务

上面的事物还没提交，也就是数据库中的数据没有被修改，如果此时想停止转账就可以取消事物，取消事务的命令是`ROLLBACK`，意思是事务回滚，回滚到开始事务之前的状态

```sql
mysql> ROLLBACK;
Query OK, 0 rows affected (0.00 sec)

mysql> SELECT * FROM `user`;
+----+------+---------+
| id | name | money   |
+----+------+---------+
|  1 | A    | 1000.00 |
|  2 | B    |  800.00 |
|  3 | C    | 1200.00 |
+----+------+---------+
3 rows in set (0.00 sec)

mysql> SET AUTOCOMMIT=1;
Query OK, 0 rows affected (0.00 sec)

```

回滚之后没有转账，最后把自动提交开启



下面是C想B成功转$100

```sql
mysql> SET AUTOCOMMIT=0;
Query OK, 0 rows affected (0.00 sec)

mysql> UPDATE `user` SET money=money-100 WHERE name='C';
Query OK, 1 row affected (0.00 sec)
Rows matched: 1  Changed: 1  Warnings: 0

mysql> UPDATE `user` SET money=money+100 WHERE name='B';
Query OK, 1 row affected (0.00 sec)
Rows matched: 1  Changed: 1  Warnings: 0

mysql> COMMIT;
Query OK, 0 rows affected (0.01 sec)

mysql> SET AUTOCOMMIT=1;
Query OK, 0 rows affected (0.00 sec)

```

`COMMIT`为提交事务，如果没有出错则提交成功，数据被持久保存

```sql
mysql> SELECT * FROM `user`;
+----+------+---------+
| id | name | money   |
+----+------+---------+
|  1 | A    | 1000.00 |
|  2 | B    |  900.00 |
|  3 | C    | 1100.00 |
+----+------+---------+
3 rows in set (0.00 sec)

```



## MySQL事务隔离级别

脏读，不可重复度和幻读都是数据库一致性问题，必须由数据库提供一定的事务隔离级别机制来解决

| 隔离级别                     | 读取数据的一致性                       | 脏读 | 不可重复度 | 幻读 |
| ---------------------------- | -------------------------------------- | ---- | ---------- | ---- |
| 未提交读（Read uncommitted） | 最低级别，只保证不读取物理上损坏的数据 | 是   | 是         | 是   |
| 已提交读（Read committed）   | 语句级                                 | 否   | 是         | 是   |
| 可重复读（Repeatable read）  | 事务级                                 | 否   | 否         | 是   |
| 可序列化（Serializable）     | 最高级别，事务级                       | 否   | 否         | 否   |

数据库事务隔离级别越严格，并发副作用越小，付出的代价越大

查看数据库事务隔离级别（MySQL 8.0+）

```sql
mysql> show variables like 'transaction_isolation';
+-----------------------+-----------------+
| Variable_name         | Value           |
+-----------------------+-----------------+
| transaction_isolation | REPEATABLE-READ |
+-----------------------+-----------------+
1 row in set (0.00 sec)

```

MySQL默认是可重复读的事务隔离级别，所以MySQL不会出现脏读和不可重复读，但是可能出现幻读