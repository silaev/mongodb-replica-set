@echo off
cd ..
if exist "log" (
    echo clean log directory
    del /q log\*
    for /d %%x in (log\*) do @rd /s /q "%%x"
) else (
    echo create log directory
    mkdir log
)

echo clean build project without integrationTest
call gradlew clean build -x integrationTest -DminTestCov=0.3

echo repeat test %1 times with --no-daemon --parallel
for /l %%x in (1, 1, %1) do gradlew clean integrationTest --no-daemon --parallel > log/log%%x.log
