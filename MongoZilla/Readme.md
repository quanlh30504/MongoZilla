
## Install and set up kafka in local computer
- Tải kafka và giải nén → đổi tên file giải nén thành kafka
- Khời chạy zookepper trên win

```plaintext
C:\kafka>bin\windows\zookeeper-server-start.bat config\zookeeper.properties
```

- Khời chạy kafka server trên win (chú ý mở cmd khác và để nguyên cmd đang chạy zookepper)

```plaintext
C:\kafka>bin\windows\kafka-server-start.bat config\server.properties
```