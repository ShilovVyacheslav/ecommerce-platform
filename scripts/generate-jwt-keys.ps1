param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("local", "docker")]
    [string]$EnvName
)

$KeysDir = "user-service/src/main/resources/keys"
$PrivateKey = "$KeysDir/$EnvName-private.pem"
$PublicKey = "$KeysDir/$EnvName-public.pem"

if ((Test-Path $PrivateKey) -and (Test-Path $PublicKey)) {
    Write-Host "Keys for '$EnvName' already exist, skipping."
    exit 0
}

New-Item -ItemType Directory -Force -Path $KeysDir | Out-Null

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KeysDir/$EnvName-private-pkcs1.pem"
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in "$KeysDir/$EnvName-private-pkcs1.pem" -out $PrivateKey
openssl rsa -pubout -in "$KeysDir/$EnvName-private-pkcs1.pem" -out $PublicKey
Remove-Item "$KeysDir/$EnvName-private-pkcs1.pem"

Write-Host "Generated $EnvName keypair in $KeysDir"