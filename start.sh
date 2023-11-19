#!/usr/bin/env bash

TEMPLATES=()
rm -rf $HOME/.amper-jb
git clone https://github.com/JetBrains/amper $HOME/.amper-jb

index=1
for dir in $HOME/.amper-jb/examples/*; do
    foldername=$(basename $dir)
    TEMPLATES+=("$dir")
    echo "($index) $foldername"
    ((index++))
done

read -p "Please select template available from Amper repository: " index
read -p "Type project name: " input_project_name

result=${TEMPLATES[${index+1}]}

project_name_underscore=$(echo "$input_project_name" | sed 's/ /_/g')
project_name_final_string=$(echo "$project_name_underscore" | tr '[:upper:]' '[:lower:]')

mkdir $project_name_final_string
mv "$result"/* "$project_name_final_string"

fleet "$project_name_final_string"