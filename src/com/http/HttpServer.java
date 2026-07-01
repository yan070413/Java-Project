package com.http;

import java.net.ServerSocket;
import java.net.Socket;

public class HttpServer {
    private static final int PORT = 8080; //固定监听 8080 端口

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) { //创建一个ServerSocket类型的对象，绑定8080窗口
            System.out.println("访问地址：http://127.0.0.1:8080");
            while (true) { //线程池
                Socket clientSocket = serverSocket.accept();  //接收
                new Thread(new HttpHandler(clientSocket)).start();  //只要有一个人的服务器发来了请求，它就会创建一个新的线程。如果删掉，同一时间只能处理一个用户，其他人全部排队阻塞，无法并发访问。
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
//用try-with-rescources语句。

//两件事：
//1 程序调用JDK自带的HttpServer，创建一个ServerSocket类型的对象，绑定本机指定端口（8080），
//底层会创建ServerSocket持续监听这个端口；

//2.开启内置线程池，用来之后同时处理多个浏览器连接，本质就是通过等待方法（accept），
// 让这个程序一直开着，有一个人的服务器发来请求，接收后（accept），就创建一个线程，
// 写完这两行代码，服务就处于等待连接的状态，控制台会打印提示 “服务已启动”。
