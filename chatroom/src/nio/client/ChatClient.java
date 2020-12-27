package nio.client;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class ChatClient {

    private static final String DEFAULT_SERVER_HOST = "127.0.0.1";
    private static final int DEFAULT_SERVER_PORT = 8888;
    private static final String QUIT = "quit";

    private static final int BUFFER = 1024;
    private String host;
    private int port;
    private SocketChannel clientChannel;
    private Selector selector;
    private ByteBuffer rBuffer = ByteBuffer.allocate(BUFFER);
    private ByteBuffer wBuffer = ByteBuffer.allocate(BUFFER);
    private Charset charset = Charset.forName(String.valueOf(StandardCharsets.UTF_8));

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public ChatClient() {
        this(DEFAULT_SERVER_HOST, DEFAULT_SERVER_PORT);
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
            //创建用户通道
            clientChannel = SocketChannel.open();
            clientChannel.configureBlocking(false);//这一步千万不能忘了
            //创建selector，并且将用户通道的connect请求注册上去
            selector = Selector.open();
            clientChannel.register(selector, SelectionKey.OP_CONNECT);
            //尝试与服务器创建连接
            clientChannel.connect(new InetSocketAddress(host, port));

            while (selector.isOpen()) {
                //一直监听selector上注册的channel请求
                selector.select();

                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (SelectionKey selectionKey : selectionKeys) {
                    //响应请求
                    handles(selectionKey);
                }
                selectionKeys.clear();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(selector);
        }
    }

    private void handles(SelectionKey selectionKey) throws IOException {
        //处理connect事件
        if (selectionKey.isConnectable()) {
            //如果能够与服务器响应了
            SocketChannel channel = (SocketChannel) selectionKey.channel();
            if (channel.isConnectionPending()) {
                channel.finishConnect(); //正式建立连接
                new Thread(new UserInputHandler(this)).start();
            }
            channel.register(selector, SelectionKey.OP_READ);
        } else if (selectionKey.isReadable()) {
            String msg = receive(clientChannel);
            if (msg.isEmpty()) {
                //服务端异常
                close(selector);
            } else {
                System.out.println(msg);
            }
        }
    }

    private String receive(SocketChannel clientChannel) throws IOException {
        rBuffer.clear();
        //一直读数据
        while (clientChannel.read(rBuffer) > 0) ;
        rBuffer.flip();
        return String.valueOf(charset.decode(rBuffer));
    }

    public void send(String msg) throws IOException {
        if (msg.isEmpty())
            return;

        wBuffer.clear();
        wBuffer.put(charset.encode(msg));
        wBuffer.flip();
        while (wBuffer.hasRemaining()) {
            clientChannel.write(wBuffer);
        }

        if (QUIT.equals(msg))
            close(selector);
    }

    public static void main(String[] args) {
        ChatClient chatClient = new ChatClient();
        chatClient.start();
    }
}