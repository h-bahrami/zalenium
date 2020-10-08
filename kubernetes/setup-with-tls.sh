#!/usr/bin/env bash

# *** VERY IMPORTANT, SET PROPER VALUES FOR THE FOLLOWING SETTINGS, THEN RUN **** 
#   Check points  
#   values.yaml
#       ingress.secretName
#       ingress.hostname
#   production_issuer
#       acme.email, acme.privateKeySecretRef.name

CLUSTER = 'cluster'
NAMESPACE = 'namspace'
INSTALL_CERT_MANAGER = 0            # 0 | 1
PAUSE_4_STEPS = 1                   # 0 | 1
CERTIFICATE_SECRET_NAME = "tls-secret-name"

function Pause(){
    echo " "
    echo " "
    echo $1 $2 $3
    if [[ $PAUSE_4_STEPS -eq 1 ]] 
    then         
        read -p "Press any key to resume ..."
    fi
    echo "############################################################################################"
}

Pause "Step 0 - Creating $CLUSTER:$NAMESPACE and other initializations"
helm repo update 
kubectl config use-context $CLUSTER
kubectl create namespace $NAMESPACE

Pause "Step 1 — Deploy Selenium Hub..."
helm install zalenium --namespace $NAMESPACE ../charts/zalenium --set ingress.tls=false --set ingress.enabled=false --set hub.serviceType=LoadBalancer
kubectl get pods, services, ingresses -n $NAMESPACE

pause "Step 2 — Installing the Kubernetes Nginx Ingress Controller"
helm install nginx-ingress --namespace $NAMESPACE stable/nginx-ingress --set controller.publishService.enabled=true
# kubectl get services -o wide -w nginx-ingress-controller

pause "Step 3 — Exposing the App Using an Ingress"
helm upgrade zalenium --namespace $NAMESPACE ../charts/zalenium --set ingress.tls=false --set ingress.enabled=true --set hub.serviceType=LoadBalancer
kubectl get services, ingresses
Write-Host "*** At this point domain names should work (hint: need to setup hosts with IP)***"

if [[ $INSTALL_CERT_MANAGER -eq 1]]
then
    pause "Step 4 — Securing the Ingress Using Cert-Manager $cluster):$NAMESPACE ..."
    kubectl apply --validate=false -f https://github.com/jetstack/cert-manager/releases/download/v0.14.1/cert-manager.crds.yaml
    Pause "Step 4.1 - Creating cert-manager namespace..."
    kubectl create namespace cert-manager
    Pause "Step 4.2 - Adding https://charts.jetstack.io to helm repos..."
    helm repo add jetstack https://charts.jetstack.io
    Pause "Step 4.3 - Installing cert-manager..."
    helm install cert-manager --version v0.14.1 --namespace cert-manager jetstack/cert-manager    
    Pause "Step 4.4 - Creating issuer, if failed run the cmd separately several times, eventauly succeeds if previous steps are ok."
    Sleep 10 # need some time have resources ready
    kubectl create -f .\production_issuer.yaml
fi

Pause "Step 5 - Enabling TLS in ingress controller, make sure IP/HOST setting is ok then continue"
helm upgrade zalenium --namespace $NAMESPACE ../charts/zalenium --set ingress.tls=true --set ingress.enabled=true --set hub.serviceType=LoadBalancer

kubectl describe certificate $CERTIFICATE_SECRET_NAME --namespace $NAMESPACE

Pause "Step 6 - Enabling basic authentication for the hub"
helm upgrade zalenium --namespace $NAMESPACE ../charts/zalenium --set ingress.tls=true --set ingress.enabled=true --set hub.basicAuth.enabled=true --set hub.serviceType=LoadBalancer

echo "cluster: $CLUSTER, namespace: $NAMESPACE, tls secret: $CERTIFICATE_SECRET_NAME"

Pause "Installation is finished, in few minutes you should be able to naviate to your host and https should be enabled."