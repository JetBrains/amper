# Required to do clean builds on TeamCity
# To build:
# $ docker build -t registry.jetbrains.team/p/amper/containers/ubuntu-22.04-clean:latest .
# To push to docker registry:
# $ docker login registry.jetbrains.team -u <FirstName.LastName>
# $ docker push registry.jetbrains.team/p/amper/containers/ubuntu-22.04-clean:latest

FROM ubuntu:22.04

RUN apt update

# wget is used by wrapper script to download JDK. The only dependency used to bootstrap Amper (or curl)
RUN apt install -y wget
