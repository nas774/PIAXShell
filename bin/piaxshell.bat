@echo off
if %BASE%a==a set BASE=..
set LIB=%BASE%/lib
set BIN=%BASE%/PIAXShell.jar
rem set BIN=%BASE%\obj
set PIAX=%LIB%/piax-2.1.0.jar;%LIB%\asm-3.2.jar
set LOGGER=%LIB%/slf4j-api-1.6.1.jar;%LIB%/simplelog-slf4j.jar;%LIB%/simple-logx.jar;
set CLASSPATH=%LIB%;%BIN%;%PIAX%;%LOGGER%
java org.github.nas774.piax.piaxshell.PIAXShell %1 %2 %3 %4 %5 %6 %7 %8 %9
