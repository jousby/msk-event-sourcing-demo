#!/bin/sh

curl -X PUT "$ELASTICSEARCH_URL/simplesourcedemo_account_transaction?pretty" -H 'Content-Type: application/json' -d'
{
  "mappings": {
    "properties": {
      "account": { "type":  "keyword" },
      "ammount": { "type":  "double" }
    }
  }
}
'

curl -X PUT "$ELASTICSEARCH_URL/simplesourcedemo_account_summary?pretty" -H 'Content-Type: application/json' -d'
{
  "mappings": {
    "properties": {
      "accountName": { "type":  "keyword" },
      "balance": { "type":  "double" }
    }
  }
}
'
