package com.http;

import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// 帖子实体类
class Post {
    public String title;
    public String content;
    public Post(String title, String content) {
        this.title = title;
        this.content = content;
    }
}

public class HttpHandler implements Runnable {
    private final Socket clientSocket;
    private static final String WEB_ROOT = "./web/";
    private static final String POST_FILE = "./postData.txt";
    public static List<Post> postList = new ArrayList<>();

    // 自动读取根目录的 postData.txt 文件，把之前保存的所有帖子加载进内存集合 postList。
    // 实现效果：重启服务器帖子不会消失。
    static {   //static静态代码块，全程只执行一次。自动执行，不需要人维护调用代码。可以换成静态方法，但需要在HttpServer中调用，不利于维护。
        File dataFile = new File(POST_FILE);
        if (dataFile.exists()) {  //try-with-resources格式，读取本地 postData.txt、把历史帖子加载到内存集合
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(dataFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null && !line.trim().isEmpty()) {  // 循环读取文件每一行，读到文件末尾返回null停止
                    String[] split = line.split("\\|\\|\\|");  // 把一行文本按 ||| 分割成数组，双反斜杠\\是转义，正则识别单个|
                    if (split.length == 2) {
                        postList.add(new Post(split[0], split[1]));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public HttpHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {  // 实现 Runnable 接口必须重写的方法
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = clientSocket.getOutputStream()) {

            String requestLine = in.readLine();  //读取请求首行（最重要，拿到 GET/POST、访问路径）
            if (requestLine == null || requestLine.isEmpty()) {return;}
            System.out.println("收到请求：" + requestLine);

            //解析请求并拆分成两个部分，一个method(如GET)，另一个urlPath(如/publishing.html)。单独取出请求方式和访问路径，分别在下面做路由判断。
            String[] lineParts = requestLine.split(" ");  //把请求第一行字符串，按空格切成数组
            String method = lineParts[0];
            String urlPath = lineParts[1];

            int contentLength = 0;
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {  //与第55行不同，读过就不能再读了，所以这个是读的第二行，
                if (headerLine.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(headerLine.split(":")[1].trim());  // 按冒号分割字符串,取冒号后面数字，去掉前后空格，转成整数
                }
            }

//            // ===== 新增：鸿蒙App专用接口 - 获取全部帖子 =====
//            if (urlPath.startsWith("/getAllPosts")) {
//                handleGetAllPosts(out);
//                return;
//            }
//
//            // ===== 新增：鸿蒙App专用接口 - 获取单条帖子 =====
//            if (urlPath.startsWith("/getPost")) {
//                handleGetPost(out, urlPath);
//                return;
//            }

            // 提交帖子接口
            if ("/savePost".equals(urlPath) && "POST".equalsIgnoreCase(method)) {
                handleSavePost(in, out, contentLength);
                return;
            }

            // ===== 新增：下面这行是原来的注释掉的，现在去掉注释，保留原逻辑 =====
            // 获取单条帖子JSON接口 /getPost?idx=数字
            // if(urlPath.startsWith("/getPost")){
            //     handleGetPost(out, urlPath);
            //     return;
            // }

            String targetFile;
            if ("/".equals(urlPath)) {
                targetFile = WEB_ROOT + "index.html";
            } else if ("/publishing.html".equals(urlPath)) {
                targetFile = WEB_ROOT + "publishing.html";
            } else if(urlPath.startsWith("/detail")){
                targetFile = WEB_ROOT + "detail.html";
            } else {
                targetFile = WEB_ROOT + urlPath.substring(1);
            }

            File file = new File(targetFile);
            if (file.exists() && file.isFile()) {
                if ("/".equals(urlPath)) {
                    sendIndexFull(out, file);
                } else {
                    sendHtmlResponse(out, file);
                }
            } else {
                send404(out);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

//    // ===== 新增：获取全部帖子接口（返回JSON） =====
//    private void handleGetAllPosts(OutputStream out) throws IOException {
//        StringBuilder json = new StringBuilder();
//        json.append("[");
//        for (int i = 0; i < postList.size(); i++) {
//            Post p = postList.get(i);
//            if (i > 0) {json.append(",");}
//            json.append("{");
//            json.append("\"title\":\"").append(p.title.replace("\"", "\\\"")).append("\",");
//            json.append("\"content\":\"").append(p.content.replace("\"", "\\\"")).append("\"");
//            json.append("}");
//        }
//        json.append("]");
//
//        StringBuilder header = new StringBuilder();
//        header.append("HTTP/1.1 200 OK\r\n");
//        header.append("Content-Type: application/json; charset=utf-8\r\n");
//        header.append("Content-Length: ").append(json.toString().getBytes(StandardCharsets.UTF_8).length).append("\r\n");
//        header.append("Connection: close\r\n\r\n");
//        out.write(header.toString().getBytes(StandardCharsets.UTF_8));
//        out.write(json.toString().getBytes(StandardCharsets.UTF_8));
//        out.flush();
//    }

    // ===== 新增：获取单条帖子接口（返回JSON） =====
//    private void handleGetPost(OutputStream out, String url) throws IOException {
//        int idx = Integer.parseInt(url.split("idx=")[1]);
//        Post p = postList.get(idx);
//        // 转义双引号防止JSON报错
//        String safeContent = p.content.replace("\"","\\\"");
//        String json = "{\"title\":\""+p.title+"\",\"content\":\""+safeContent+"\"}";
//        StringBuilder header = new StringBuilder();
//        header.append("HTTP/1.1 200 OK\r\n");
//        header.append("Content-Type: application/json; charset=utf-8\r\n");
//        header.append("Content-Length: ").append(json.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
//        header.append("Connection: close\r\n\r\n");
//        out.write(header.toString().getBytes(StandardCharsets.UTF_8));
//        out.write(json.getBytes(StandardCharsets.UTF_8));
//        out.flush();
//    }

    private void handleSavePost(BufferedReader in, OutputStream out, int len) throws IOException {
        char[] buf = new char[len];
        in.read(buf);
        String raw = new String(buf);
        String title = "", content = "";
        String[] params = raw.split("&");
        for (String p : params) {
            String[] kv = p.split("=");
            if(kv.length < 2) {continue;}
            if ("title".equals(kv[0])) {
                title = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
            if ("content".equals(kv[0])) {
                content = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        if(title.isBlank() || content.isBlank()){
            StringBuilder resp = new StringBuilder();
            resp.append("HTTP/1.1 302 Found\r\n");
            resp.append("Location: /publishing.html\r\n");
            resp.append("Connection: close\r\n\r\n");
            out.write(resp.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            return;
        }
        Post newPost = new Post(title, content);
        synchronized (postList) {
            postList.add(newPost);
        }
        saveToFile(newPost);
        System.out.println("新帖子发布成功：" + title);

        StringBuilder resp = new StringBuilder();
        resp.append("HTTP/1.1 302 Found\r\n");
        resp.append("Location: /\r\n");
        resp.append("Connection: close\r\n\r\n");
        out.write(resp.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void saveToFile(Post post) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(POST_FILE, true), StandardCharsets.UTF_8))) {
            bw.write(post.title + "|||" + post.content);
            bw.newLine();
        }
    }

    private void sendIndexFull(OutputStream out, File indexFile) throws IOException {
        String html = new String(readFile(indexFile), StandardCharsets.UTF_8);
        String latestHtml;
        String allHtml;

        synchronized (postList) {
            if (postList.isEmpty()) {
                latestHtml = "<div class=\"empty-tip\">暂无帖子，点击右上角发布按钮创建第一条帖子</div>";
                allHtml = "<div class=\"empty-tip\">还没有其他用户发布帖子</div>";
            } else {
                // 最新5条
                int latestCount = Math.min(5, postList.size());
                StringBuilder latestSb = new StringBuilder();
                for(int i = postList.size()-latestCount; i < postList.size(); i++){
                    Post p = postList.get(i);
                    String brief = p.content.length() > 60 ? p.content.substring(0,60)+"……" : p.content;
                    // a标签跳转详情页 /detail?idx=下标
                    latestSb.append("<a href=\"/detail?idx="+i+"\" style=\"text-decoration:none;color:inherit;\">")
                            .append("<div class=\"post-card\">")
                            .append("<div class=\"post-title\">"+p.title+"</div>")
                            .append("<div class=\"post-content\">"+brief+"</div>")
                            .append("</div></a>");
                }
                latestHtml = latestSb.toString();

                // 全部帖子（包含上面5条）
                StringBuilder allSb = new StringBuilder();
                for(int i = 0; i < postList.size(); i++){
                    Post p = postList.get(i);
                    String brief = p.content.length() > 60 ? p.content.substring(0,60)+"……" : p.content;
                    allSb.append("<a href=\"/detail?idx="+i+"\" style=\"text-decoration:none;color:inherit;\">")
                            .append("<div class=\"post-card\">")
                            .append("<div class=\"post-title\">"+p.title+"</div>")
                            .append("<div class=\"post-content\">"+brief+"</div>")
                            .append("</div></a>");
                }
                allHtml = allSb.toString();
            }
        }

        html = html.replaceFirst("<div id=\"postList\" class=\"empty-tip\">[\\s\\S]*?</div>", "<div id=\"postList\" class=\"empty-tip\">\n" + latestHtml + "\n</div>");
        html = html.replaceFirst("<div id=\"otherPostList\" class=\"empty-tip\">[\\s\\S]*?</div>", "<div id=\"otherPostList\" class=\"empty-tip\">\n" + allHtml + "\n</div>");

        byte[] finalHtml = html.getBytes(StandardCharsets.UTF_8);
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 200 OK\r\n");
        header.append("Content-Type: text/html; charset=utf-8\r\n");
        header.append("Content-Length: ").append(finalHtml.length).append("\r\n");
        header.append("Connection: close\r\n\r\n");
        out.write(header.toString().getBytes(StandardCharsets.UTF_8));
        out.write(finalHtml);
        out.flush();
    }

    private void sendHtmlResponse(OutputStream out, File file) throws IOException {
        byte[] html = readFile(file);
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 200 OK\r\n");
        header.append("Content-Type: text/html; charset=utf-8\r\n");
        header.append("Content-Length: ").append(html.length).append("\r\n");
        header.append("Connection: close\r\n\r\n");
        out.write(header.toString().getBytes(StandardCharsets.UTF_8));
        out.write(html);
        out.flush();
    }

    private void send404(OutputStream out) throws IOException {
        String html404 = "<html><body><h1>404 Not Found</h1><p>页面不存在</p></body></html>";
        byte[] body = html404.getBytes(StandardCharsets.UTF_8);
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 404 Not Found\r\n");
        header.append("Content-Type: text/html; charset=utf-8\r\n");
        header.append("Content-Length: ").append(body.length).append("\r\n");
        header.append("Connection: close\r\n\r\n");
        out.write(header.toString().getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private byte[] readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        }
    }
}
