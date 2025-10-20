#!/bin/bash

# === VARIABLES ===
SRC_DIR="src/nofy/p17"
WEBAPP_DIR="../test_sprint/web"
BUILD_DIR="../test_sprint/build"
CLASSES_DIR="$BUILD_DIR/WEB-INF/classes"
LIB_DIR="lib"
JAR_NAME="framework.jar"
LIB_TEST="../test_sprint/lib"

# === PRÉPARATION ===
echo "=== PRÉPARATION DU BUILD ==="
# Créer le répertoire de build
mkdir -p $CLASSES_DIR

# === COMPILATION ===
echo "=== COMPILATION DES CLASSES JAVA ==="
javac -cp "$LIB_DIR/*" -d $CLASSES_DIR $SRC_DIR/*.java

# === COPIE DES FICHIERS WEBAPP ===
echo "=== COPIE DES FICHIERS WEBAPP ==="
cp -R $WEBAPP_DIR/* $BUILD_DIR

# === CRÉATION DU JAR ===
echo "=== CRÉATION DU JAR ==="
jar -cvf "$LIB_DIR/$JAR_NAME" -C "$CLASSES_DIR" .

# === DÉPLOIEMENT ===
echo "=== DÉPLOIEMENT DU JAR DANS LE PROJET TEST ==="
cp "$LIB_DIR/$JAR_NAME" "$LIB_TEST"

# === NETTOYAGE ===
echo "=== NETTOYAGE ==="
rm -rf $BUILD_DIR

echo "=== DÉPLOIEMENT TERMINÉ ==="
