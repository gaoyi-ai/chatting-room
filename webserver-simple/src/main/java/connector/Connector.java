package connector;

import processor.ServletProcessor;
import processor.StaticProcessor;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;

/**
 * 使用NIO模型，仅用于处理连接
 */
public class Connector implements Runnable {

    private static final int DEFAULT_PORT = 8888;
    private ServerSocketChannel server;
    private Selector selector;
    private int port;

    public Connector() {
        this(DEFAULT_PORT);
    }

    public Connector(int port) {
        this.port = port;
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    @Override
    public void run() {
        try {

            // 创建一个ServerSocketChannel通道
            server = ServerSocketChannel.open();
            // 设置通道为未非阻塞（默认是阻塞的）
            server.configureBlocking(false);
            // 绑定到监听端口
            server.socket().bind(new InetSocketAddress(port));

            // 创建selector，帮我们监听ACCEPT事件
            selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("启动服务器， 监听端口：" + port + "...");

            while (true) {
                // select()函数阻塞式的监听事件的发生
                selector.select();

                // 逐个处理监听到的事件
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (SelectionKey key : selectionKeys) {
                    // 处理被触发的事件
                    handles(key);
                }

                // 清理本次监听到的事件，以便处理下一次监听到的事件
                selectionKeys.clear();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(selector);
        }
    }

    /**
     * 处理事件
     *
     * @param key
     * @throws IOException
     */
    private void handles(SelectionKey key) throws IOException {
        // ACCEPT
        if (key.isAcceptable()) {
            // 获取当前通道的ServerSocketChannel
            ServerSocketChannel server = (ServerSocketChannel) key.channel();
            // 接收连接
            SocketChannel client = server.accept();
            // 非阻塞式
            client.configureBlocking(false);
            // 让selector开始监听改客户端上的READ事件，处理客户端给服务端发送的请求
            client.register(selector, SelectionKey.OP_READ);
        }
        // READ
        else {

            // 获取客户端SocketChannel
            SocketChannel client = (SocketChannel) key.channel();
            /**
             * InputStream、OutPutStream 都是只支持阻塞式IO的，而Channel可以支持阻塞式和非阻塞式IO
             * SocketChannel本身是注册在selector上面，当有注册过需要监听的事件发生时，也就是说当SocketChannel是和selector一起使用的时候
             * 必须保证Channel是处于非阻塞式的状态，如果这时改变这条channel的状态就会有异常被抛出，此时channel处于一个无效的状态
             *
             * 那么既然想取得并操作 InputStream、OutPutStream，只能选择阻塞式的操作方法
             * 并且还要想一个办法避免selector抛出任何的异常，则可以调用 key.cancel();表示不希望这个channel继续被selector轮询监听了，彻底的把这条channel和
             * selector之间的关系解锁，做完这样一个操作之后，这条channel就和selector没有关系，就可以再次把这条channel恢复到阻塞状态，然后对于一个阻塞状态的
             * channel去取得socket的InputStream、OutPutStream
             */
            key.cancel();
            client.configureBlocking(true);

            Socket clientSocket = client.socket();
            InputStream input = clientSocket.getInputStream();
            OutputStream output = clientSocket.getOutputStream();

            Request request = new Request(input);
            request.parse();

            Response response = new Response(output);
            response.setRequest(request);

            if (request.getRequestURI().startsWith("/servlet/")) {
                // 处理动态资源请求
                ServletProcessor processor = new ServletProcessor();
                processor.process(request, response);
            } else {
                // 处理静态资源请求
                StaticProcessor processor = new StaticProcessor();
                processor.process(request, response);
            }

            // 每次处理完成直接关闭连接（后续可以优化成客户端xxx时间没有发送请求，关闭连接）
            close(client);
        }
    }

    private void close(Closeable closable) {
        if (closable != null) {
            try {
                closable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
