@echo off
setlocal



rem Paramètres

set SRC_DIR=src\nofy\p17\
set LIB_DIR=lib
set CLASSES_DIR=classes

REM Créer le dossier de sortie s'il n'existe pas
if not exist "%CLASSES_DIR%" (
    mkdir "%CLASSES_DIR%"
)


set nomAppli="sprint"
set temp="temp"
set web="web"

if exist %temp% (
    rmdir /s /q .\"%temp%"
)
mkdir "%temp%"
mkdir "%temp%"\classes


rem compilation
rem /chemin/vers/java19/bin/javac -target 19 -source 19 -d ./classes/ ./src/*
rem javac -d ./classes/ ./src/*

javac -cp "%LIB_DIR%\*" -d "%CLASSES_DIR%" %SRC_DIR%\*.java  

Xcopy .\"classes" .\"%temp%"\classes /E /H /C /I /Y

cd %temp%/classes
jar -cf "%nomAppli%.jar" *


move "%nomAppli%.jar" ../../../test_sprint/lib

@REM cd ../..
@REM rmdir /s /q .\"%temp%"