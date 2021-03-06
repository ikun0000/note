# RocketMQ消息投递与接收

[详细的消息投递样例]( https://github.com/apache/rocketmq/blob/master/docs/cn/RocketMQ_Example.md )



首先使用maven创建一个demo，然后导入RocketMQ的依赖坐标

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-client</artifactId>
    <version>4.3.0</version>
</dependency>
```



## 基础的消息发送与接收

RocketMQ发送最基本的三种类型的消息：同步消息、异步消息和单向消息。其中前两种消息是可靠的，因为会有发送是否成功的应答。



### 普通消息接收

```java
package org.example.simple;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.List;

public class Consumer {

    public static void main(String[] args) throws MQClientException {
        // 实例化消费者，需要指定消费者所属的组
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("GroupA");
        // 连接RocketMQ的Name Server
        consumer.setNamesrvAddr("10.10.10.246:9876");
        // 订阅一个或者多个Topic，以及Tag来过滤需要消费的消息
        consumer.subscribe("TopicA", "TagA||TagB");

        // 注册回调实现类来处理从broker拉取回来的消息
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                synchronized (this) {
                    for (MessageExt msg : msgs) {
                        System.out.printf("Message ID: %s%n", msg.getMsgId());
                        System.out.printf("Body CRC: %d%n", msg.getBodyCRC());
                        byte[] bodyMsg = msg.getBody();
                        System.out.printf("Message: %s%n", new String(bodyMsg, 0, bodyMsg.length));
                        System.out.println("--------------------");
                    }
                }

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        // 开始消费
        consumer.start();
        System.out.println("Consumer Started...");
    }

}

```

上面提到的概念：

> * **消息（Message）**
>   消息系统所传输信息的物理载体，生产和消费数据的最小单位，每条消息必须属于一个主题。RocketMQ中每个消息拥有唯一的Message ID，且可以携带具有业务标识的Key。系统提供了通过Message ID和Key查询消息的功能。
>
> * **标签（Tag）**
>   为消息设置的标志，用于同一主题下区分不同类型的消息。来自同一业务单元的消息，可以根据不同业务目的在同一主题下设置不同标签。标签能够有效地保持代码的清晰度和连贯性，并优化RocketMQ提供的查询系统。消费者可以根据Tag实现对不同子主题的不同消费逻辑，实现更好的扩展性。
>
> * **消费者组（Consumer Group）**
>   同一类Consumer的集合，这类Consumer通常消费同一类消息且消费逻辑一致。消费者组使得在消息消费方面，实现负载均衡和容错的目标变得非常容易。要注意的是，消费者组的消费者实例必须订阅完全相同的Topic。RocketMQ 支持两种消息模式：集群消费（Clustering）和广播消费（Broadcasting）。 
>
> * **名字服务（Name Server）**
>   名称服务充当路由消息的提供者。生产者或消费者能够通过名字服务查找各主题相应的Broker IP列表。多个Namesrv实例组成集群，但相互独立，没有信息交换。
>
> * **主题（Topic）**
>   表示一类消息的集合，每个主题包含若干条消息，每条消息只能属于一个主题，是RocketMQ进行消息订阅的基本单位。

首先在main函数的第一行先实例化了一个**推动式消费者**，并设置了消费者组

> **推动式消费（Push Consumer）**
>
> Consumer消费的一种类型，该模式下Broker收到数据后会主动推送给消费端，该消费模式一般实时性较高。

之后设置Name Server的地址和设置监听的主题和可以接收的Tag，如果接收所有Tag的消息就用 `*` 匹配，如果是多个标签可以使用 `||` 来分割标签，然后需要一个 `MessageListenerConcurrently` 的子类来处理Broker推送过来的消息（因为现在用的是推动式消费），并且是多线程执行，`msgs` 就是消息列表，在设置完处理消息的方法之后就可以开始监听消息了。



### 发送同步消息

这种可靠性同步地发送方式使用的比较广泛，比如：重要的消息通知，短信通知。

```java
package org.example.simple;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.io.UnsupportedEncodingException;

public class SyncProducer {

    public static void main(String[] args) throws MQClientException, UnsupportedEncodingException, RemotingException, InterruptedException, MQBrokerException {
        // 实例化生产者，需要指定生产者组名
        DefaultMQProducer producer = new DefaultMQProducer("GroupA");
        // 设置Name Server地址
        producer.setNamesrvAddr("10.10.10.246:9876");
        // 启动生产者实例
        producer.start();

        // 实例化消息
        Message message1 = new Message("TopicA",    // 目标Topic
                "TagA",                              // 消息的Tag
                "First Message".getBytes(RemotingHelper.DEFAULT_CHARSET));  // 消息体
        Message message2 = new Message("TopicA",
                "TagB",
                "Second Message".getBytes(RemotingHelper.DEFAULT_CHARSET));

        SendResult sendResult1 = producer.send(message1);      // 发送消息
        SendResult sendResult2 = producer.send(message2);

        // 打印SendResult内容
        show(sendResult1);
        show(sendResult2);

        // 关闭生产者实例
        producer.shutdown();
    }

    // 显示SendResult
    private static void show(SendResult sendResult) {
        System.out.printf("Message ID: %s%n", sendResult.getMsgId());
        System.out.printf("Transaction ID: %s%n", sendResult.getTransactionId());
        System.out.printf("Region ID: %s%n", sendResult.getRegionId());
        System.out.printf("Status: %s%n", sendResult.getSendStatus());
        System.out.println("--------------------");
    }

}

```

上面提到的概念：

> * **消息生产者（Producer）**
>
>   负责生产消息，一般由业务系统负责生产消息。一个消息生产者会把业务应用系统里产生的消息发送到broker服务器。RocketMQ提供多种发送方式，同步发送、异步发送、顺序发送、单向发送。同步和异步方式均需要Broker返回确认信息，单向发送不需要。
>
> * **生产者组（Producer Group）**
>
>   同一类Producer的集合，这类Producer发送同一类消息且发送逻辑一致。如果发送的是事务消息且原始生产者在发送之后崩溃，则Broker服务器会联系同一生产者组的其他生产者实例以提交或回溯消费。

首先在main函数开始的时候先实例化一个生产者，然后和消费者一样设置Name Server服务器的地址。然后生产者需要先启动实例才能使用，然后使用 `Message` 封装发送的消息再使用 `send` 方法发送出去。最后需要手动关闭生产者实例。



### 发送异步消息

异步消息通常用在对响应时间敏感的业务场景，即发送端不能容忍长时间地等待Broker的响应。

```java
package org.example.simple;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.CountDownLatch2;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;

import java.util.concurrent.TimeUnit;

public class AsyncProducer {

    public static void main(String[] args) throws Exception {
        // 实例化生产者，需要指定生产者组名
        DefaultMQProducer producer = new DefaultMQProducer("group1");
        // 设置Name Server地址
        producer.setNamesrvAddr("10.10.10.246:9876");
        // 启动生产者实例
        producer.start();
        // 设置消息重投
        producer.setRetryTimesWhenSendAsyncFailed(0);

        int messageCount = 100;
        // 实例化一个计数器
        CountDownLatch2 countDownLatch2 = new CountDownLatch2(messageCount);

        for (int i = 0; i < messageCount; i++) {
            // 封装消息
            Message message = new Message("TopicA",
                    "TagA",
                    "OrderID1",     // 业务标识的Key
                    ("Async Message" + i).getBytes(RemotingHelper.DEFAULT_CHARSET));

            // 发送消息，并且传入SendCallback的实现异步处理返回的ACK
            producer.send(message, new SendCallback() {
                // 成功发送并接收到ACK
                @Override
                public void onSuccess(SendResult sendResult) {
                    show(sendResult);
                    countDownLatch2.countDown();
                }

                // 发送失败
                @Override
                public void onException(Throwable e) {
                    System.err.println("Exception: " + e);
                    countDownLatch2.countDown();
                }
            });
        }

        // 等待消息响应后关闭生产者实例退出程序
        countDownLatch2.await(5, TimeUnit.SECONDS);
        producer.shutdown();
    }

    // 显示SendResult
    private static void show(SendResult sendResult) {
        System.out.printf("Message ID: %s%n", sendResult.getMsgId());
        System.out.printf("Transaction ID: %s%n", sendResult.getTransactionId());
        System.out.printf("Region ID: %s%n", sendResult.getRegionId());
        System.out.printf("Status: %s%n", sendResult.getSendStatus());
        System.out.println("--------------------");
    }

}

```

上面提到的概念：

> * **消息重投**
>
>   生产者在发送消息时，同步消息失败会重投，异步消息有重试，oneway没有任何保证。消息重投保证消息尽可能发送成功、不丢失，但可能会造成消息重复，消息重复在RocketMQ中是无法避免的问题。消息重复在一般情况下不会发生，当出现消息量大、网络抖动，消息重复就会是大概率事件。另外，生产者主动重发、consumer负载变化也会导致重复消息。如下方法可以设置消息重试策略：
>
>   * retryTimesWhenSendFailed:同步发送失败重投次数，默认为2，因此生产者会最多尝试发送retryTimesWhenSendFailed + 1次。不会选择上次失败的broker，尝试向其他broker发送，最大程度保证消息不丢。超过重投次数，抛出异常，由客户端保证消息不丢。当出现RemotingException、MQClientException和部分MQBrokerException时会重投。
>   * retryTimesWhenSendAsyncFailed:异步发送失败重试次数，异步重试不会选择其他broker，仅在同一个broker上做重试，不保证消息不丢。
>   * retryAnotherBrokerWhenNotStoreOK:消息刷盘（主或备）超时或slave不可用（返回状态非SEND_OK），是否尝试发送到其他broker，默认false。十分重要消息可以开启。

前面几行代码还是一如既往的初始化生产者实例，之后需要设置消息重投次数，如果不设置就使用默认值。因为是使用异步发送消息，所以我们需要一个 `CountDownLatch2` 而不是JUC提供的 `CountDownLatch` ，这是RocketMQ自己封装的。然后在循环中封装消息并发送，当然，在发送的同时要传入一个 `SendCallback` 的实现去处理返回的ACK包或者处理发送失败的异常。



### 发送单向消息

这种方式主要用在不特别关心发送结果的场景，例如日志发送。

```java
package org.example.simple;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.io.UnsupportedEncodingException;

public class OnewayProducer {

    public static void main(String[] args) throws MQClientException, UnsupportedEncodingException, RemotingException, InterruptedException, MQBrokerException {
        // 实例化生产者，需要指定生产者组名
        DefaultMQProducer producer = new DefaultMQProducer("GroupA");
        // 设置Name Server地址
        producer.setNamesrvAddr("10.10.10.246:9876");
        // 启动生产者实例
        producer.start();

        Message message = new Message("TopicA",
                "TagA",
                "One way Producer".getBytes(RemotingHelper.DEFAULT_CHARSET));

        // 使用sendOneWay直接发送而不接收ACK包
        producer.sendOneway(message);

        // 等待关闭退出
        Thread.sleep(5000);
        producer.shutdown();
    }

}

```

单向发送是虽简单的了，只是在同步发送的基础上在发送消息的时候使用 `sendOneway` 而非使用 `send` 。



## 顺序消息投递与接收

消息有序指的是可以按照消息的发送顺序来消费(FIFO)。RocketMQ可以严格的保证消息有序，可以分为分区有序或者全局有序。

在默认的情况下消息发送会采取Round Robin轮询方式把消息发送到不同的queue(分区队列)；而消费消息的时候从多个queue上拉取消息，这种情况发送和消费是不能保证顺序。但是如果控制发送的顺序消息只依次发送到同一个queue中，消费的时候只从这个queue上依次拉取，则就保证了顺序。当发送和消费参与的queue只有一个，则是全局有序；如果多个queue参与，则为分区有序，即相对每个queue，消息都是有序的。

```java
package org.example.order;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class OrderedProducer {

    public static void main(String[] args) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("GroupA");
        producer.setNamesrvAddr("10.10.10.246:9876");
        producer.start();

        List<OrderStep> orderStepList = buildOrders();

        for (OrderStep orderStep : orderStepList) {
            // 直接把对象序列化发送到消息队列中
            ByteArrayOutputStream baos = new ByteArrayOutputStream(100);
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(orderStep);
            Message message = new Message("TopicA",
                    "TagA",
                    "OrderId1",
                    baos.toByteArray());

            /**
             * 该send方法第一个参数为发送的消息，第二个参数是MessageQueueSelector的实现
             * 用来告诉程序如何选择消息队列，第三个参数会传递给MessageQueueSelector的select
             * 方法的第二个参数
             */
            SendResult sendResult = producer.send(message, new MessageQueueSelector() {
                @Override
                public MessageQueue select(List<MessageQueue> mqs, Message msg, Object arg) {
                    Long id = (Long) arg;
                    long index = id % mqs.size();
                    return mqs.get((int) index);
                }
            }, orderStep.getOrderId());

            show(sendResult);
        }

        producer.shutdown();
    }

    // 显示SendResult
    private static void show(SendResult sendResult) {
        System.out.printf("Message ID: %s%n", sendResult.getMsgId());
        System.out.printf("Transaction ID: %s%n", sendResult.getTransactionId());
        System.out.printf("Region ID: %s%n", sendResult.getRegionId());
        System.out.printf("Status: %s%n", sendResult.getSendStatus());
        System.out.println("--------------------");
    }

    // 产生订单用例
    private static List<OrderStep> buildOrders() {
        List<OrderStep> orderStepList = new ArrayList<>();

        orderStepList.add(new OrderStep(1539L, "create"));
        orderStepList.add(new OrderStep(1565L, "create"));
        orderStepList.add(new OrderStep(1539L, "payment"));
        orderStepList.add(new OrderStep(1535L, "create"));
        orderStepList.add(new OrderStep(1565L, "payment"));
        orderStepList.add(new OrderStep(1535L, "payment"));
        orderStepList.add(new OrderStep(1565L, "complete"));
        orderStepList.add(new OrderStep(1539L, "push"));
        orderStepList.add(new OrderStep(1535L, "complete"));
        orderStepList.add(new OrderStep(1539L, "complete"));
        return orderStepList;
    }

    // 订单实体类
    public static class OrderStep implements Serializable {
        private long orderId;
        private String desc;

        public OrderStep(long orderId, String desc) {
            this.orderId = orderId;
            this.desc = desc;
        }

        public long getOrderId() {
            return orderId;
        }

        public void setOrderId(long orderId) {
            this.orderId = orderId;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        @Override
        public String toString() {
            return "OrderStep{" +
                    "orderId=" + orderId +
                    ", desc='" + desc + '\'' +
                    '}';
        }
    }

}

```

> * **普通顺序消息（Normal Ordered Message）**
>
>   普通顺序消费模式下，消费者通过同一个消费队列收到的消息是有顺序的，不同消息队列收到的消息则可能是无顺序的。
>
> * **严格顺序消息（Strictly Ordered Message）**
>
>   严格顺序消息模式下，消费者收到的所有消息均是有顺序的。

这里发送的消息有点特别，消息体不是字符串，而是一个序列化的对象。在发送消息的时候使用有参数 `MessageQueueSelector` 的方法，并且自己实现 `MessageQueueSelector` 选择队列。`MessageQueueSelector` 的 `select` 方法的第一个参数是可以选择的消息队列，第二个参数是 `send` 方法的第三个参数。除了这些其他和普通的消息发送没什么区别。



这是对应的接收程序：

```java
package org.example.order;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

public class OrderedConsumer {

    public static void main(String[] args) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("GroupA");
        consumer.setNamesrvAddr("10.10.10.246:9876");

        /**
         * 设置Consumer第一次启动是从队列头部开始消费还是队列尾部开始消费
         * 如果非第一次启动，那么按照上次消费的位置继续消费
         */
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.subscribe("TopicA", "TagA");

        // 使用MessageListenerOrderly的实现去处理每条队列的顺序消息
        consumer.registerMessageListener(new MessageListenerOrderly() {

            @Override
            public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
                context.setAutoCommit(true);
                for (MessageExt msg : msgs) {
                    ByteArrayInputStream bais = new ByteArrayInputStream(msg.getBody());
                    try {
                        // 反序列化传输的对象并打印Queue ID和对象内容
                        ObjectInputStream ois = new ObjectInputStream(bais);
                        OrderedProducer.OrderStep orderStep = (OrderedProducer.OrderStep) ois.readObject();
                        synchronized (this) {
                            System.out.printf("Queue ID: %d%n", msg.getQueueId());
                            System.out.printf("Payload: %s%n", orderStep);
                            System.out.println("--------------------");
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }

                return ConsumeOrderlyStatus.SUCCESS;
            }
        });

        consumer.start();
        System.out.println("Consumer Started...");

    }

}

```

在监听消息的时候使用 `MessageListenerOrderly` 的实现，保证顺序性。在 `consumeMessage` 内就是反序列化原来的对象并打印。



## 延迟消息发送与接收

RocketMQ支持发送延迟消息，即消息发送之后不会立即消费，而是等待一段时间之后再消费。

延迟消息可以用在电商里，提交了一个订单就可以发送一个延时消息，1h后去检查这个订单的状态，如果还是未付款就取消订单释放库存。

```java
package org.example.schedule;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.List;

public class ScheduledMessageConsumer {

    public static void main(String[] args) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("GroupA");
        consumer.setNamesrvAddr("10.10.10.246:9876");
        consumer.subscribe("TopicA", "*");

        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                for (MessageExt msg : msgs) {
                    synchronized (this) {
                        System.out.printf("Message ID: %s%n", msg.getMsgId());
                        System.out.printf("Message: %s%n", new String(msg.getBody(), 0, msg.getBody().length));
                        System.out.println("--------------------");
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        consumer.start();
    }

}

```

以上是消息的消费程序，他就是一个普通的消息消费程序而已

```java
package org.example.schedule;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;

public class ScheduledMessageProducer {

    public static void main(String[] args) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("GroupA");
        producer.setNamesrvAddr("10.10.10.246:9876");
        producer.start();

        for (int i = 0; i < 10; i++) {
            Message message = new Message("TopicA",
                    ("Scheduled message" + i).getBytes(RemotingHelper.DEFAULT_CHARSET));
            /**
             * 设置消息的延时级别，RocketMQ不支持自定义延时时间，只能选择延时级别
             * 这条消息会在10s后才会被消费
             * 
             * 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
             * 对应的延时级别为1~18
             */
            message.setDelayTimeLevel(3);
            producer.send(message);
        }

        producer.shutdown();
    }

}

```

发送延迟消息和普通消息没有多大区别，只是在实例化消息之后调用 `setDelayTimeLevel` 设置消息的延迟级别

> 现在RocketMq并不支持任意时间的延时，需要设置几个固定的延时等级，从1s到2h分别对应着等级1到18 消息消费失败会进入延时消息队列，消息发送时间与设置的延时等级和重试次数有关，详见代码`SendMessageProcessor.java`
>
> ```java
> // org/apache/rocketmq/store/config/MessageStoreConfig.java
> 
> private String messageDelayLevel = "1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h";
> ```

 

## 消息广播

广播消费模式下，相同Consumer Group的每个Consumer实例都接收全量的消息。

这个模式是在消费者端设置的。假如有一个TopicA，有5个消费者在普通模式下监听消息，当TopicA收到一条消息之后只会发送给一个消费者处理这条消息，如果5个消费者是以广播模式监听消息的话，那么当TopicA收到消息之后会给5个消费者都发一份消息。

```java
package org.example.broadcast;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;

public class BroadcastProducer {

    public static void main(String[] args) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("GroupA");
        producer.setNamesrvAddr("10.10.10.246:9876");
        producer.start();

        Message message = new Message("TopicA", "Boardcast Message".getBytes(RemotingHelper.DEFAULT_CHARSET));
        producer.send(message);

        producer.shutdown();
    }

}

```

消息生产者并没有什么不一样

```java
package org.example.broadcast;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;

import java.util.List;

public class BroadcastConsumer {

    public static void main(String[] args) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("GroupA");
        consumer.setNamesrvAddr("10.10.10.246:9876");
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        // 设置消息监听模式为广播模式
        consumer.setMessageModel(MessageModel.BROADCASTING);
        consumer.subscribe("TopicA", "*");

        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                for (MessageExt msg : msgs) {
                    synchronized (this) {
                        System.out.printf("Message ID: %s%n", msg.getMsgId());
                        System.out.printf("Message: %s%n", new String(msg.getBody(), 0, msg.getBody().length));
                        System.out.println("--------------------");
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        consumer.start();
    }

}

```

消息消费者只需要在消费之前调用 `setMessageModel` 设置 `MessageModel.BROADCASTING` 广播模式



## 批量发送消息

```java
package org.example.batch;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;

import java.util.ArrayList;
import java.util.List;

public class NoSplitProducer {

    public static void main(String[] args) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("GroupA");
        producer.setNamesrvAddr("10.10.10.246:9876");
        producer.start();

        List<Message> messageList = new ArrayList<>();
        messageList.add(new Message("TopicA", "Batch message 1".getBytes(RemotingHelper.DEFAULT_CHARSET)));
        messageList.add(new Message("TopicA", "Batch message 2".getBytes(RemotingHelper.DEFAULT_CHARSET)));
        messageList.add(new Message("TopicA", "Batch message 3".getBytes(RemotingHelper.DEFAULT_CHARSET)));
        producer.send(messageList);

        producer.shutdown();
    }

}

```

`DefaultMQProducer` 提供了 `send(Collection<Message>)` 的发送消息方法，允许传入一个 `Collection` 封装好的消息，但是这些消息的大小不能超过4MB，如果超过4MB则需要分片发送：

```java
package org.example.batch;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SplitProducer {

    public static void main(String[] args) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("GroupA");
        producer.setNamesrvAddr("10.10.10.246:9876");
        producer.start();

        List<Message> messageList = new ArrayList<>();
        messageList.add(new Message("TopicA", "Batch message 1".getBytes(RemotingHelper.DEFAULT_CHARSET)));
        messageList.add(new Message("TopicA", "Batch message 2".getBytes(RemotingHelper.DEFAULT_CHARSET)));
        messageList.add(new Message("TopicA", "Batch message 3".getBytes(RemotingHelper.DEFAULT_CHARSET)));
        ListSplitter listSplitter = new ListSplitter(messageList);

        while (listSplitter.hasNext()) {
            producer.send(listSplitter.next());
        }

        producer.shutdown();
    }

    private static class ListSplitter implements Iterator<List<Message>> {
        private static final int SIZE_LIMIT = 1024 * 1024 * 4;
        private final List<Message> messageList;
        private int currentIndex = 0;

        private ListSplitter(List<Message> messageList) {
            this.messageList = messageList;
        }

        @Override
        public boolean hasNext() {
            return currentIndex < messageList.size();
        }

        @Override
        public List<Message> next() {
            int startIndex = getStartIndex();
            int nextIndex = startIndex;
            int totalSize = 0;

            // 循环计算开始索引和结束索引
            for (; nextIndex < messageList.size(); nextIndex++) {
                Message message = messageList.get(nextIndex);
                int tmpSize = calcMessageSize(message);
                // 如果之前的Message大小加上当前Message的大小超过限制则停止
                if (tmpSize + totalSize > SIZE_LIMIT) {
                    break;
                } else {
                    totalSize += tmpSize;
                }
            }

            List<Message> subList = messageList.subList(startIndex, nextIndex);
            currentIndex = nextIndex;
            return subList;
        }

        // 防止单个Message大于4MB，如果一个Message大于4MB则直接跳过不发送
        private int getStartIndex() {
            Message currentMessage = messageList.get(currentIndex);
            int tmpSize = calcMessageSize(currentMessage);
            while (tmpSize > SIZE_LIMIT) {
                currentIndex++;
                Message message = messageList.get(currentIndex);
                tmpSize = calcMessageSize(message);
            }

            return currentIndex;
        }

        // 计算Message的大小
        private int calcMessageSize(Message message) {
            int tmpSize = message.getTopic().length() + message.getBody().length;
            Map<String, String> properties = message.getProperties();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                tmpSize += entry.getKey().length() + entry.getValue().length();
            }
            tmpSize = tmpSize + 20; // 增加⽇日志的开销20字节
            return tmpSize;
        }
    }

}

```

发送消息和之前的一样，只是这里需要自己定义一个类实现消息分片，这里使用 `Iterator` 来实现消息的分片



## 消息过滤

RocketMQ的 `DefaultMQPushConsumer` 在使用 `subscribe` 订阅Topic的时候可以提供允许消费的Tag，使用 `||` 分割，或者使用 `*` 全部匹配：

```java
DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("CID_EXAMPLE");
consumer.subscribe("TOPIC", "TAGA || TAGB || TAGC");
```
消费者将接收包含TAGA或TAGB或TAGC的消息。

```java
DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("CID_EXAMPLE");
consumer.subscribe("TOPIC", "*");
```

但是限制是一个消息只能有一个标签，这对于复杂的场景可能不起作用。在这种情况下，可以使用SQL表达式筛选消息。SQL特性可以通过发送消息时的属性来进行计算。在RocketMQ定义的语法下，可以实现一些简单的逻辑

```
------------
| message  |
|----------|  a > 5 AND b = 'abc'
| a = 10   |  --------------------> Gotten
| b = 'abc'|
| c = true |
------------
------------
| message  |
|----------|   a > 5 AND b = 'abc'
| a = 1    |  --------------------> Missed
| b = 'abc'|
| c = true |
------------
```

RocketMQ只定义了一些基本语法：

- 数值比较，比如：**>，>=，<，<=，BETWEEN，=；**
- 字符比较，比如：**=，<>，IN；**
- **IS NULL** 或者 **IS NOT NULL；**
- 逻辑符号 **AND，OR，NOT；**

常量支持类型为：

- 数值，比如：**123，3.1415；**
- 字符，比如：**'abc'，必须用单引号包裹起来；**
- **NULL**，特殊的常量
- 布尔值，**TRUE** 或 **FALSE**



```java
package org.example.filter;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;

public class FilterProducer {

    public static void main(String[] args) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("GroupA");
        producer.setNamesrvAddr("10.10.10.246:9876");
        producer.start();

        Message message1 = new Message("TopicA",
                "TagA",
                "Filter Message 1".getBytes(RemotingHelper.DEFAULT_CHARSET));
        // 给消息添加用户自定义属性
        message1.putUserProperty("a", "500");
        producer.send(message1);

        Message message2 = new Message("TopicA",
                "TagA",
                "Filter Message 2".getBytes(RemotingHelper.DEFAULT_CHARSET));
        message2.putUserProperty("a", "200");
        producer.send(message2);

        producer.shutdown();
    }

}

```

使用filter过滤消息的时候可以调用 `Message` 的 `putUserProperty` 添加属性条件，consumer需要根据这些条件判断是否消费此消息。

```java
package org.example.filter;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.MessageSelector;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.List;

public class FilterConsumer {

    public static void main(String[] args) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("GroupA");
        consumer.setNamesrvAddr("10.10.10.246:9876");
        // 订阅Topic和使用MessageSelector.bySql设置SQL筛选条件
        consumer.subscribe("TopicA", MessageSelector.bySql("a > 300"));
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                for (MessageExt msg : msgs) {
                    synchronized (this) {
                        System.out.printf("Message ID: %s%n", msg.getMsgId());
                        System.out.printf("Message: %s%n", new String(msg.getBody(), 0, msg.getBody().length));
                        System.out.println("--------------------");
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        consumer.start();
    }

}

```

消费消息和之前的差不多，只是在调用 `subscribe` 订阅Topic的时候的第二个参数使用 ` MessageSelector.bySql` 写上SQL表达式



### 坑

正常启动会出现下面这个异常：

```
![pay_system_single](C:\Users\kk\Desktop\note\RocketMQ\img\pay_system_single.png)Exception in thread "main" org.apache.rocketmq.client.exception.MQClientException: CODE: 1  DESC: The broker does not support consumer to filter message by SQL92
```

那是因为broker没有开启filter，打开**${ROCKETMQ_HOME}/conf/broker.conf**，并添加以下配置：

```properties
enablePropertyFilter=true
```

然后重启broker：

```shell
$ nohup sh bin/mqbroker -n 10.10.10.246:9876 -c conf/broker.conf autoCreateTopicEnable=true &
```



## 分布式事务与事务消息



### 分布式事务

首先有一个支付系统，在用户支付完成后会提交支付订单到支付订单数据库中，然后再计算手续费并把手续费提交到手续费数据库中：

![](./img/pay_system_single.png)

由于支付订单和对应的手续费是要一起出现的，所以只需要开启一个事务即可：

```java
// 开启支付模块事务
// 开启手续费事务
try {
    // 提交支付订单
    // 计算手续费
    // 提交手续费
} catch (Exception e) {
    // 回滚支付订单事务
    // 回滚手续费事务
} 
```

在但应用中并不是什么问题，但是手续费计算不是要求立即计算出来的，所以我们把支付模块和手续费计算模块分割成两个微服务：

![](./img/pay_system.png)

此时问题就出现了：

在这个架构中，每一步都可能失败，如果没有处理好就会导致支付订单和手续费不一致，有可能是支付订单更新了手续费没有更新，也有可能是手续费更新了但支付订单没有更新。如果没有处理话导致坏账就等着被财务砍死吧！

对于手续费系统来说比较简单，如果消费消息失败则可以让支付系统重发即可，最大的问题就在于无法保证更新本地事务和发送消息的一致性，即无法保证更新订单成功后发送消息一定成功或者发送消息成功但是更新订单一定成功。无论是先更新订单还是先发送消息都无法保证。

如果我们先发送消息：

![](./img/send_message_first.png)

如果先发送消息在更新本地事务会怎么样能，假如第一步发送消息成功，但是第二步更新订单失败产生了事务回滚，那么第一步发送的消息是不能撤回的，此时手续费已经消费了消息并提交了手续费，那么就导致了手续费被更新了但订单没被更新。

如果反过来：

![](./img/commit_transaction_first.png)

我们先执行本地事务再提交支付成功消息，代码结构可这样：

```java
// 开启支付订单事务
try {
    // 提交支付订单事务
    // 提交事务
} catch (Exception) {
    // 回滚支付订单事务
}
// 发送支付成功消息
```

如果这样写代码那如果消息发送失败就没办法回滚事务了，这个也很好解决：

```java
// 开启支付订单事务
try {
    // 提交支付订单事务
    // 发送支付成功消息
    // 提交事务
} catch (Exception) {
    // 回滚支付订单事务
}
```

只需要把发送支付成功消息放到try块里面执行就好了，这看似没什么问题，但是如果因为网路抖动导致支付系统没有接收到MQ的响应，从而导致支付系统认为发送消息失败并回滚了本地事务。此时手续费计算系统已经消费消息并写入到手续费数据库了，这又导致了不一致。



### 事务消息

RocketMQ提供事务消息实现分布式事务，保证了要么本地事务和发送消息同时成功或同时失败

![](./img/transaction_message.png)

具体做法是首先producer先发送一个half消息到RocketMQ中，通知其开启一个事务，这个half消息并不是不完整的消息。half消息和普通消息不同在于在事务提交之前这个half消息对于consumer来说这不可见的，consumer无法消费这个消息。在half消息发送成功之后就可以执行本地事务，根据事务执行的结果决定是否提交事务消息。如果事务提交成功，则发送确认消息到RocketMQ，此时consumer就可以消费这条消息了。如果事务回滚了，那么发送回滚消息到RocketMQ，RocketMQ就会删除这条消息，consumer也就没有办法消费这条消息。这样就解决了一致性问题了。

> RocketMQ实现consumer不可见也很简单：如果消息是half消息，将备份原消息的主题与消息消费队列，然后改变主题为RMQ_SYS_TRANS_HALF_TOPIC。由于消费组未订阅该主题，故消费端无法消费half类型的消息，然后RocketMQ会开启一个定时任务，从Topic为RMQ_SYS_TRANS_HALF_TOPIC中拉取消息进行消费，根据生产者组获取一个服务提供者发送回查事务状态请求，根据事务状态来决定是提交或回滚消息。

对于以上做法中，如果食物消息提交或回滚失败了怎么办？对于这个问题，RocketMQ提供了事务反查机制，需要注册一个消息反查接口用于反查本地事务状态。如果RocketMQ没有收到提交或回滚请求就会定期反查，然后根据反查结果确定提交还是回滚。

RocketMQ执行事务消息的流程如下：

![](./img/transaction_message_step.png)



具体的Producer如下，Consumer方不变：

```java
package org.example.transaction;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;

import java.util.concurrent.*;

public class TransactionProducer {

    public static void main(String[] args) throws Exception {
        // 初始化事务生产者
        TransactionMQProducer producer = new TransactionMQProducer("GroupA");
        producer.setNamesrvAddr("10.10.10.246:9876");
        // 给事务生产者提供的线程池
        ExecutorService executorService = new ThreadPoolExecutor(2,
                5,
                100,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(2000),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName("client-transaction-msg-check-thread");
                        return thread;
                    }
                });
        producer.setExecutorService(executorService);

        // 初始化该事务消息对应的本地事务和本地事务反查对象
        TransactionListener transactionListener = new TransactionListenerImpl();
        producer.setTransactionListener(transactionListener);

        producer.start();

        Message successMessage = new Message("TopicA",  "TagA", "Success".getBytes(RemotingHelper.DEFAULT_CHARSET));
        Message failureMessage = new Message("TopicA", "TagA", "Failure".getBytes(RemotingHelper.DEFAULT_CHARSET));

        // 把消息发送到事务里
        SendResult sendResult1 = producer.sendMessageInTransaction(successMessage, Integer.parseInt("1"));
        SendResult sendResult2 = producer.sendMessageInTransaction(failureMessage, null);

        System.out.printf("Success Message: %s%n", sendResult1.getSendStatus());
        System.out.printf("Failure Message: %s%n", sendResult2.getSendStatus());

        producer.shutdown();
    }

}

```

执行事务消息需要 `TransactionMQProducer` 实例并给唯一的生产组名。然后就可以提供一个线程池给 `TransactionMQProducer` 处理检查请求i。

然后需要提供 `TransactionListener` 来执行本地事务和反查方法：

```java
package org.example.transaction;

import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

public class TransactionListenerImpl implements TransactionListener {

    // 这个方法就是执行本地事务的
    // 这个方法会在调用sendMessageInTransaction之后执行
    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        if (arg == null) {
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
        return LocalTransactionState.COMMIT_MESSAGE;
    }

    // 这个方法是反查本地事务状态，根据本地事务状态提交或回滚事务消息
    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        return LocalTransactionState.COMMIT_MESSAGE;
    }
}

```

`executeLocalTransaction` 为提交half消息成功后执行本地事务的方法，并根据本地事务执行结果返回事务消息状态。

事务消息共有三种状态，提交状态、回滚状态、中间状态：

- TransactionStatus.CommitTransaction: 提交事务，它允许消费者消费此消息。
- TransactionStatus.RollbackTransaction: 回滚事务，它代表该消息将被删除，不允许被消费。
- TransactionStatus.Unknown: 中间状态，它代表需要检查消息队列来确定状态。

`checkLocalTransaction` 用于检查消息对应的本地事务状态并返回事务消息状态。

`Message` 和 `MessageExt` 可以调用 `getTransactionId` 方法获取这个消息的事务ID。

在提供好 `TransactionListener` 之后和发送普通消息差不多，但是发送消息的方法不是 `send` 而是 `sendMessageInTransaction` ，`sendMessageInTransaction` 的第二个参数会传递给 `executeLocalTransaction` 的第二个参数。之后的事就由rocketmq-client来完成了。



### 事务消息使用上的限制

1. 事务消息不支持延时消息和批量消息。
2. 为了避免单个消息被检查太多次而导致半队列消息累积，我们默认将单个消息的检查次数限制为 15 次，但是用户可以通过 Broker 配置文件的 `transactionCheckMax`参数来修改此限制。如果已经检查某条消息超过 N 次的话（ N = `transactionCheckMax` ） 则 Broker 将丢弃此消息，并在默认情况下同时打印错误日志。用户可以通过重写 `AbstractTransactionalMessageCheckListener` 类来修改这个行为。
3. 事务消息将在 Broker 配置文件中的参数 transactionTimeout 这样的特定时间长度之后被检查。当发送事务消息时，用户还可以通过设置用户属性 CHECK_IMMUNITY_TIME_IN_SECONDS 来改变这个限制，该参数优先于 `transactionTimeout` 参数。
4. 事务性消息可能不止一次被检查或消费。
5. 提交给用户的目标主题消息可能会失败，目前这依日志的记录而定。它的高可用性通过 RocketMQ 本身的高可用性机制来保证，如果希望确保事务消息不丢失、并且事务完整性得到保证，建议使用同步的双重写入机制。
6. 事务消息的生产者 ID 不能与其他类型消息的生产者 ID 共享。与其他类型的消息不同，事务消息允许反向查询、MQ服务器能通过它们的生产者 ID 查询到消费者。