# Check And Retrieve

Checks the person exists and retrieves the members protection enhancements data

Calls to this API must be made by an authorised user.

**URL**: `/members-protections-and-enhancements/check-and-retrieve`

**Method**: `POST`

**Required Request Headers**:

| Header Name   | Header Value   | Description                                |
|---------------|----------------|--------------------------------------------|
| Authorization | Bearer {TOKEN} | A valid bearer token from the auth service |

**Request Body**

The data section contains the actual user answers and other stored data. The content will depend on the specific questions answered and will not be documented in detail here

| Field Name  | Description                           | Data Type | Mandatory/Optional | Notes |
|-------------|---------------------------------------|-----------|--------------------|-------|
| firstName   | The user's first name                 | String    | Mandatory          |       |
| lastName    | The user's last name                  | String    | Mandatory          |       |
| dateOfBirth | The user's date of birth (YYYY-MM-DD) | String    | Mandatory          |       |
| identifier  | The NINO of the user                  | String    | Mandatory          |       |
| psaCheckRef | The PSA reference                     | String    | Mandatory          |       |

***Example request body***

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "dateOfBirth": "1949-02-27",
  "nino": "AA123456A",
  "psaCheckRef": "PSA12345678A"
}
```

## Response

The data is returned as a sequence of Protection Records - the record is documented below

| Field Name               | Description                             | Data Type | Mandatory/Optional | Notes         |
|--------------------------|-----------------------------------------|-----------|--------------------|---------------|
| protectionReference      | The reference                           | String    | Optional           |               |
| type                     | The type of protection                  | Object    | String             | * See below   |
| status                   | The status                              | Object    | String             | ** See below  |
| protectedAmount          | The amount                              | Int       | Optional           |               |
| lumpSumAmount            | The lump sum amount                     | Int       | Optional           |               |
| lumpSumPercentage        | The percentage this lump sum represents | Int       | Optional           |               |
| enhancementFactor        | Enhancement Factor                      | Number    | Optional           |               |
| pensionCreditLegislation | Relevant legislation                    | String    | Optional           | *** See below |
 
Note that these enumerations may get added to over time

 * type - One of:
   "FIXED PROTECTION 2016"
   "INDIVIDUAL PROTECTION 2014"
   "INDIVIDUAL PROTECTION 2016"
   "PRIMARY PROTECTION"
   "ENHANCED PROTECTION"
   "FIXED PROTECTION"
   "FIXED PROTECTION 2014"
   "PENSION CREDIT RIGHTS"
   "INTERNATIONAL ENHANCEMENT (S221)"
   "INTERNATIONAL ENHANCEMENT (S224)"
   "FIXED PROTECTION 2016 LTA"
   "INDIVIDUAL PROTECTION 2014 LTA"
   "INDIVIDUAL PROTECTION 2016 LTA"
   "PRIMARY PROTECTION LTA"
   "ENHANCED PROTECTION LTA"
   "FIXED PROTECTION LTA"
   "FIXED PROTECTION 2014 LTA"
   "FIXED PROTECTION"

 ** status - One of:
    "OPEN"
    "DORMANT"
    "WITHDRAWN"

 *** pensionCreditLegislation - One of:
    "PARAGRAPH 18 SCHEDULE 36 FINANCE ACT 2004"
    "SECTION 220 FINANCE ACT 2004"


### Success response

**Code**: `200 OK`

**Response Body**

**Example Response Body**

```json
{
  "protectionRecords": [
    {
      "protectionReference": "EPRO2345678901A",
      "type": "ENHANCED PROTECTION",
      "status": "OPEN",
      "lumpSumPercentage": 12
    },
    {
      "type": "PRIMARY PROTECTION",
      "status": "DORMANT",
      "lumpSumAmount": 234876,
      "enhancementFactor": 0.54
    }
  ]
}
```

### Error Responses
**Code**: `400 BAD_REQUEST`
This response can occur when invalid json is passed

**Code**: `401 UNAUTHORIZED`
This response can occur when a call is made by any user without an authorized session or doesn't have a PSA or PSP Id

**Code**: `403 FORBIDDEN`
This response can occur when the downstream rejects access

**Code**: `404 NOT FOUND`
This response is returned when no results are found (this may move to NO_CONTENT in future to distinguish from url not found)

**Code**: `500 INTERNAL_SERVER_ERROR`
An exception occurred when trying to process the request
