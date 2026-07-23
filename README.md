# 后端启动指令
$env:PATHOCHECK_DB_PASSWORD="123456"
Set-Location E:\PathoCheck\backend
.\mvnw.cmd spring-boot:run
或使用Windows Powershell运行startbackend.ps1
# 前端启动指令
Set-Location E:\PathoCheck\frontend
npm install
npm run dev
或使用Windows Powershell运行startfrontend.ps1
http://localhost:5173/