
# https://www.digitalocean.com/community/tutorials/how-to-set-up-an-nginx-ingress-on-digitalocean-kubernetes-using-helm


$cluster = 'ttc1'
$namespace = 'z2'
$install_cert_manager = 0
$pause_4_steps = 0

function pause($msg) {
    write-host " "
    write-host "-------------------------------------------------------------------"
    write-host "$($msg)"
    if ($pause_4_steps) {
        write-host "Press any key to continue..."
        [void][System.Console]::ReadKey($true)        
    }
}



Pause "Step 0 - Creating namespace $($namespace) in cluster: $($cluster) and Setting kubectl current context"
kubectl create namespace $namespace

Pause "Step 1 — Deploy Selenium Hub..."
helm install zalenium --namespace $namespace ../charts/zalenium --set ingress.tls=false --set ingress.enabled=false --set hub.serviceType=LoadBalancer
kubectl get pods, services, ingresses -n $namespace

pause "Step 2 — Installing the Kubernetes Nginx Ingress Controller"
helm install nginx-ingress --namespace $namespace stable/nginx-ingress --set controller.publishService.enabled=true
# kubectl get services -o wide -w nginx-ingress-controller

pause "Step 3 — Exposing the App Using an Ingress"
helm upgrade zalenium --namespace $namespace ../charts/zalenium --set ingress.tls=false --set ingress.enabled=true --set hub.serviceType=LoadBalancer
kubectl get services, ingresses
Write-Host "*** At this point domain names should work (hint: need to setup hosts with IP)***"

if ($install_cert_manager) {
    pause "Step 4 — Securing the Ingress Using Cert-Manager $($cluster):$($namespace) ..."
    kubectl apply --validate=false -f https://github.com/jetstack/cert-manager/releases/download/v0.14.1/cert-manager.crds.yaml
    Pause "Step 4.1 - Creating cert-manager namespace..."
    kubectl create namespace cert-manager
    Pause "Step 4.2 - Adding https://charts.jetstack.io to helm repos..."
    helm repo add jetstack https://charts.jetstack.io
    Pause "Step 4.3 - Installing cert-manager..."
    helm install cert-manager --version v0.14.1 --namespace cert-manager jetstack/cert-manager
    Pause "Step 4.4 - Creating issuer, if failed run the cmd separately several times, eventauly succeeds if previous steps are ok."
    kubectl create -f .\production_issuer.yaml
}

Pause "Step 5 - Enabling TLS in ingress controller, make sure IP/HOST setting is ok then continue"
helm upgrade zalenium --namespace $namespace ../charts/zalenium --set ingress.tls=true --set ingress.enabled=true --set hub.serviceType=LoadBalancer

kubectl describe certificate kubernetes-tls-secret-z1 --namespace $namespace

Pause "Step 6 - Enabling basic authentication for the hub"
helm upgrade zalenium --namespace $namespace ../charts/zalenium --set ingress.tls=true --set ingress.enabled=true --set hub.basicAuth.enabled=true --set hub.serviceType=LoadBalancer

Pause "Installation is finished, in few minutes you should be able to naviate to your host and https should be enabled."