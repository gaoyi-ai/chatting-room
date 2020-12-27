package bio.Client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class UserInputHandler implements Runnable {
    private ChatClient chatClient;

    public UserInputHandler(ChatClient chatClient) {
        this.chatClient = chatClient;
    }


    @Override
    public void run() {
        //等待用户输入信息
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in)
        );

        while (true) {
            try {
                String msg = reader.readLine();
                //向服务器发送消息
                chatClient.sendMsg(msg);
                if (chatClient.readyToQuit(msg))
                    break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

