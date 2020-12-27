package nio.server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class ChatServer {
    private static final int DEFAULT_PORT = 8888;
    private static final String QUIT = "quit";
    private static final int BUFFER = 1024;
    private int port;

    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private ByteBuffer rBuffer = ByteBuffer.allocate(BUFFER);
    private ByteBuffer wBuffer = ByteBuffer.allocate(BUFFER);
    private Charset charset = Charset.forName(String.valueOf(StandardCharsets.UTF_8));

    public ChatServer() {
        this(DEFAULT_PORT);
    }

    public ChatServer(int port) {
        this.port = port;
    }

    public boolean readyToQuit(String msg) {
        return QUIT.equals(msg);
    }

    public void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void start() {
        try {
            //创建ServerSocketChannel通道
            serverSocketChannel = ServerSocketChannel.open();
            //设置非阻塞模式，默认情况也是阻塞调用的
            serverSocketChannel.configureBlocking(false);
            //绑定端口号
            serverSocketChannel.bind(new InetSocketAddress(port));
            //创建selector
            selector = Selector.open();
            //将accept事件注册到selector上
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("服务器启动成功，监听端口号：" + port + "...");

            //开始进入监听模式
            while (true) {
                //阻塞式调用
                selector.select();

                //获取所有的监听事件，
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (SelectionKey selectionKey : selectionKeys) {
                    //处理事件
                    handles(selectionKey);
                }
                //将已经处理完成的事件进行清空
                selectionKeys.clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(selector);
        }
    }

    /**
     * 处理事件 处理accept事件 和 read事件
     *
     * @param selectionKey 与selector绑定的channel的key
     */
    private void handles(SelectionKey selectionKey) throws IOException {
        if (selectionKey.isAcceptable()) {
            //处理accept事件
            //先要获取ServerSocketChannel
            ServerSocketChannel server = (ServerSocketChannel) selectionKey.channel();
            //获取对应的客户端的通道
            SocketChannel socketChannel = server.accept();
            socketChannel.configureBlocking(false);
            //将客户端通道绑定到selector上，监听read事件
            socketChannel.register(selector, SelectionKey.OP_READ);
            System.out.println("客户端" + socketChannel.socket().getPort() + ":已经连接");
        } else if (selectionKey.isReadable()) {
            //处理read事件
            SocketChannel clientChannel = (SocketChannel) selectionKey.channel();
            String fwdMsg = receive(clientChannel);

            if (fwdMsg.isEmpty()) {
                //接不到消息了，那么就把这个通道移除
                selectionKey.cancel();
                //通知selector有注册的通道被移除，更新状态
                selector.wakeup();
            } else {
                //转发消息
                forwardMessage(clientChannel, fwdMsg);
                if (readyToQuit(fwdMsg)) {
                    selectionKey.cancel();
                    selector.wakeup();
                }
            }
        }
    }

    /**
     * 转发消息
     *
     * @param clientChannel 客户端通道
     * @param fwdMsg        转发的消息
     */
    private void forwardMessage(SocketChannel clientChannel, String fwdMsg) throws IOException {
        //keys方法区别于selectedKeys,这个方法返回的是接下来需要被处理的通道key
        //而keys则返回与selector绑定的所有通道key
        //跳过ServerSocketChannel和本身
        for (SelectionKey selectionKey : selector.keys()) {
            SelectableChannel channel = selectionKey.channel();
            if (channel instanceof ServerSocketChannel)
                System.out.println("客户端" + clientChannel.socket().getPort() + "：" + fwdMsg);
            else if (selectionKey.isValid() && !channel.equals(clientChannel)) {
                wBuffer.clear();
                //写入消息
                wBuffer.put(charset.encode("客户端" + clientChannel.socket().getPort() + "：" + fwdMsg));
                //转换为读模式
                wBuffer.flip();
                //有数据就一直读
                while (wBuffer.hasRemaining())
                    ((SocketChannel) channel).write(wBuffer);
            }
        }
    }

    /**
     * 从客户通道上读取消息
     *
     * @param clientChannel 客户通道
     * @return 消息
     */
    private String receive(SocketChannel clientChannel) throws IOException {
        //将当前指针置于初始位置，覆盖已有的消息（清空消息）
        rBuffer.clear();
        //不停的向缓存中写
        while (clientChannel.read(rBuffer) > 0) ;
        //由写模式到读模式
        rBuffer.flip();
        return String.valueOf(charset.decode(rBuffer));
    }

    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer();
        chatServer.start();
    }
}