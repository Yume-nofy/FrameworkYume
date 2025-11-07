#!/bin/bash

# === VARIABLES ===
# Changement: La compilation doit partir de la racine des sources (src/)
# afin de compiler toutes les classes et préserver la structure des packages.
SRC_ROOT_DIR="src" 
LIB_DIR="lib"
JAR_NAME="framework.jar"
TEMP_BUILD_DIR="temp_classes"
# Répertoire de déploiement du JAR dans le projet test
LIB_TEST="../../test_sprint/lib"

# === PRÉPARATION ===
echo "=== PRÉPARATION DU BUILD DU FRAMEWORK ==="
rm -rf $TEMP_BUILD_DIR # Nettoyage initial
mkdir -p $TEMP_BUILD_DIR
mkdir -p $LIB_DIR
SOURCES_LIST="framework_sources.txt"

# === COMPILATION ===
echo "=== COMPILATION DES CLASSES JAVA DU FRAMEWORK ==="

# 1. Trouver récursivement tous les fichiers .java dans 'src'
find $SRC_ROOT_DIR -name "*.java" > $SOURCES_LIST

echo "Fichiers Framework à compiler:"
cat $SOURCES_LIST

# 2. Compiler en utilisant -sourcepath pour préserver la structure des packages (nofy/p17/).
# Le classpath $LIB_DIR/* est utilisé pour les dépendances externes du framework.
javac -cp "$LIB_DIR/*" \
      -d $TEMP_BUILD_DIR \
      -sourcepath $SRC_ROOT_DIR \
      @$SOURCES_LIST

COMPILE_RESULT=$?
rm $SOURCES_LIST

if [ $COMPILE_RESULT -ne 0 ]; then
    echo "❌ Erreur de compilation du Framework"
    exit 1
fi

echo "✅ Compilation du Framework réussie"


# === CRÉATION DU JAR ===
echo "=== CRÉATION DU JAR ==="
# Créer le JAR à partir des classes dans le répertoire temporaire.
# L'option -C $TEMP_BUILD_DIR . garantit que le JAR contient nofy/p17/...
jar -cvf "$LIB_DIR/$JAR_NAME" -C "$TEMP_BUILD_DIR" .

# === DÉPLOIEMENT ===
echo "=== DÉPLOIEMENT DU JAR DANS LE PROJET TEST ==="
# Créer le répertoire de lib du test s'il n'existe pas
mkdir -p $LIB_TEST
# Copier le JAR final vers la destination de déploiement
cp "$LIB_DIR/$JAR_NAME" "$LIB_TEST"

# === NETTOYAGE ===
echo "=== NETTOYAGE ==="
# Supprimer le répertoire de compilation temporaire uniquement
rm -rf $TEMP_BUILD_DIR

echo "=== DÉPLOIEMENT TERMINÉ ==="