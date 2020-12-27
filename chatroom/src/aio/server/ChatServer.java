package aio.server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {

    private static final String LOCALHOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    private static final String QUIT = "quit";
    private static final int BUFFER = 1024;

    private AsynchronousServerSocketChannel serverChannel;
    private AsynchronousChannelGroup asynchronousChannelGroup;
    private List<ClientHandler> connectedClients;
    private Charset charset = StandardCharsets.UTF_8;
    private int port;

    public ChatServer(int port) {
        this.port = port;
        connectedClients = new ArrayList<>();
    }

    public ChatServer() {
        this(DEFAULT_PORT);
    }

    public void start() {
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(10);
            asynchronousChannelGroup = AsynchronousChannelGroup.withThreadPool(executorService);

            serverChannel = AsynchronousServerSocketChannel.open(asynchronousChannelGroup);
            serverChannel.bind(new InetSocketAddress(LOCALHOST, port));
            System.out.println("服务器已经启动成功，随时等待客户端连接...");

            while (true) {
                // 一直调用accept函数,接收要与服务端建立连接的用户
                serverChannel.accept(null, new AcceptHandler());
                // 阻塞式调用,防止占用系统资源
                System.in.read();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(serverChannel);
        }
    }

    private class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, Object> {
        @Override
        public void completed(AsynchronousSocketChannel clientChannel, Object attachment) {
            if (serverChannel.isOpen())
                // 继续等待监听新的客户端的连接请求
                serverChannel.accept(null, this);

            // 处理当前已连接的客户端的数据读写
            if (clientChannel != null && clientChannel.isOpen()) {
                // 为该新连接的用户创建handler,用于读写操作
                ClientHandler clientHandler = new ClientHandler(clientChannel);

                ByteBuffer buffer = ByteBuffer.allocate(BUFFER);
                addClient(clientHandler);
                clientChannel.read(buffer, buffer, clientHandler);
            }

        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            System.out.println("连接失败：" + exc.getMessage());
        }
    }

    private class ClientHandler implements CompletionHandler<Integer, ByteBuffer> {

        private AsynchronousSocketChannel clientChannel;

        public ClientHandler(AsynchronousSocketChannel clientChannel) {
            this.clientChannel = clientChannel;
        }

        public AsynchronousSocketChannel getClientChannel() {
            return clientChannel;
        }

        @Override
        public void completed(Integer result, ByteBuffer buffer) {
            //buffer不为空的时候，这要执行的是read之后的回调方法
            if (buffer != null) {
                if (result <= 0) {
                    //客户端异常，将客户端从连接列表中移除
                    removeClient(this);
                } else {
                    buffer.flip();
                    String fwdMsg = receive(buffer);
                    System.out.println(getClientName(clientChannel) + fwdMsg);
                    forwardMsg(clientChannel, fwdMsg);
                    buffer.clear();

                    // 如果客户端发送的是quit退出消息，则把客户移除监听的客户列表
                    if (readyToQuit(fwdMsg)) {
                        removeClient(this);
                    } else {
                        // 如果不是则继续等待读取用户输入的信息
                        clientChannel.read(buffer, buffer, this);
                    }
                }
            }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            System.out.println("读写操作失败：" + exc.getMessage());
        }
    }

    private synchronized void addClient(ClientHandler clientHandler) {
        connectedClients.add(clientHandler);
        System.out.println(getClientName(clientHandler.getClientChannel()) + "已经连接");
    }

    private synchronized void removeClient(ClientHandler clientHandler) {
        AsynchronousSocketChannel clientChannel = clientHandler.getClientChannel();
        connectedClients.remove(clientHandler);
        System.out.println(getClientName(clientChannel) + "已经断开连接");
        close(clientChannel);
    }

    private void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean readyToQuit(String msg) {
        return QUIT.equals(msg);
    }

    private synchronized String receive(ByteBuffer buffer) {
        return String.valueOf(charset.decode(buffer));
    }

    private synchronized void forwardMsg(AsynchronousSocketChannel clientChannel, String fwdMsg) {
        for (ClientHandler connectedHandler : connectedClients) {
            AsynchronousSocketChannel client = connectedHandler.getClientChannel();
            if (!client.equals(clientChannel)) {
                try {
                    //将消息存入缓存区中
                    ByteBuffer buffer = charset.encode(getClientName(client) + fwdMsg);
                    //写给每个客户端
                    client.write(buffer, null, connectedHandler);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private String getClientName(AsynchronousSocketChannel clientChannel) {
        int port = -1;
        try {
            InetSocketAddress remoteAddress = (InetSocketAddress) clientChannel.getRemoteAddress();
            port = remoteAddress.getPort();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "客户端[" + port + "]:";
    }

    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer();
        chatServer.start();
    }
}
