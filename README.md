# paypal-example
Sample application of Paypal

## How to use

### Step 1
Create sandbox account of Paypal

https://developer.paypal.com

### Set env vars
Set facilitator's account info to env vars.

```
export PAYPAL_USERNAME=xxxx
export PAYPAL_PASSWORD=xxx
export PAYPAL_APIKEY=xxx
```

You can see this information 

Dashboard -> Sandbox -> Account -> Profile

API_KEY is displayed as `Signature`

### Run

```
sbt run
```
