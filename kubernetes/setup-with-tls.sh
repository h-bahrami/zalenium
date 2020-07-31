#!/usr/bin/env bash

# https://www.digitalocean.com/community/tutorials/how-to-set-up-an-nginx-ingress-on-digitalocean-kubernetes-using-helm

NAMESPACE = 'z1'

echo "Step 0 - Creating namespace $NAMESPACE"
kubectl create namespace $NAMESPACE

echo "Step 1 — Setting Up Hello World Deployments"
helm install zalenium --namespace $NAMESPACE ../charts/zalenium --set ingress.tls=false --set ingress.enabled=false --set hub.serviceType=LoadBalancer
sleep 1
kubectl get pods,services,ingresses -n $NAMESPACE

echo "Step 2 — Installing the Kubernetes Nginx Ingress Controller"
helm install nginx-ingress --namespace $NAMESPACE stable/nginx-ingress --set controller.publishService.enabled=true
# kubectl get services -o wide -w nginx-ingress-controller

echo "Step 3 — Exposing the App Using an Ingress"
sleep 5
helm upgrade zalenium --namespace $NAMESPACE ../charts/zalenium --set ingress.tls=false --set ingress.enabled=true --set hub.serviceType=LoadBalancer
kubectl get services,ingresses
echo "*** At this point domain names should work (hint: need to setup hosts with IP)***"

echo "Step 4 — Securing the Ingress Using Cert-Manager..."
kubectl apply --validate=false -f https://github.com/jetstack/cert-manager/releases/download/v0.14.1/cert-manager.crds.yaml

echo "Step 4.1 - Creating cert-manager namespace..."
sleep 2
kubectl create namespace cert-manager

echo "Step 4.2 - Adding https://charts.jetstack.io to helm repos..."
sleep 2
helm repo add jetstack https://charts.jetstack.io

echo "Step 4.3 - Installing cert-manager..."
sleep 2
helm install cert-manager --version v0.14.1 --namespace cert-manager jetstack/cert-manager

echo "Step 4.4 - Creating issuer, if failed run the cmd separately several times, eventauly succeeds if previous steps are ok."
sleep 10
kubectl create -f ./production_issuer.yaml

echo "Step 5 - Enabling TLS in ingress controller, make sure IP/HOST setting is ok then continue"
sleep 2
helm upgrade zalenium --namespace $NAMESPACE ../charts/zalenium --set ingress.tls=true --set ingress.enabled=true --set hub.serviceType=LoadBalancer
sleep 2
kubectl describe certificate kubernetes-tls-secret-z1 --namespace $NAMESPACE

echo "Step 6 - Enabling basic authentication for the hub"
sleep 2
helm upgrade zalenium --namespace $NAMESPACE ../charts/zalenium --set ingress.tls=true --set ingress.enabled=true --set hub.basicAuth.enabled=true --set hub.serviceType=LoadBalancer

echo "Installation is finished, in few minutes you should be able to naviate to your host and https should be enabled."