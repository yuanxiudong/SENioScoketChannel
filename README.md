#SENioSocket说明：

Java最常见使用网络编程的方式是创建ServerSocket和Socket对象，前者建立服务器并监听客户端连接。
后者代表客户端连接。
服务端代码示例：
```
    ServerSocket server = new ServerSocket();
    server.bind(new InetSocketAddress(localPort));
    Socket socket = server.accept();
    ReadThread readThread = new ReadThread(socket);
    readThread.start();
```
客户端代码示例：
```
    Socket client = new Socket();
    client.connect(new InetSocketAddress(serverIP,serverPort));
    ReadThread readThread = new ReadThread(socket);
    readThread.start();
```
再上述示例中server.accept和client.connect都是阻塞操作，属于耗时操作需要再一个异步线程中运行。
而每个连接Socket的数据读取也是阻塞的，也需要在一个异步线程中执行，假设有N个客户端连接，那么
服务器就需要开启N+1个线程，目前操作系统对线程的数量是有限制的，而且线程及线程切换也需要消耗
系统CPU，内存等资源。
NIO采取了一种类似观察者模式的方式，由一个线程去轮询所有的socket连接发生的事件，例如：
1. 有客户端连接事件
2. 有数据读取事件。
3. 通道缓冲空间可以写数据事件。
4. 可以连接事件等。
采用主动轮询而非阻塞当前线程的方式，避免了创建多个线程的问题。

### 注意
1. 每个SelectionKey都是表示一种状态，例如readable,writable,acceptable等。
2. 每个SelectionKey代表通道当前满足的状态，必须处理掉这些状态，否则会不停上报状态就绪事件。
3. 例如readable代表通道的读取缓存有数据了，如果不及时将这些数据读取出来，就会连续收到非常多重复
的readable事件。
4. 收到了状态就绪事件需要立即同步处理，否则就会收到大量重复事件。
5. writable谨慎注册，因为大部分通信不可能一直进行，那么通道的写缓存大部分时间都是空闲的
这就意味着通道大部分时间处于写就绪状态，如果注册了writable就会一致收到写就绪时间，非常耗CPU。
6. 如果想在多线程方面优化系统，就只能考虑都Selector的方式了，不能采用讲SelectionKey分发到其它线程处理。