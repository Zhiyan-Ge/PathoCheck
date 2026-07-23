# 后端启动指令
$env:PATHOCHECK_DB_PASSWORD="你的mysql密码"

cd D:\PathoCheck\backend
.\mvnw.cmd spring-boot:run

# 前端启动指令
cd D:\PathoCheck\frontend
& "C:\Program Files\nodejs\npm.cmd" run dev

http://localhost:5173/