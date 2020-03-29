import React, { useState } from "react";
import { useHistory } from "react-router-dom";
import { postData } from "../service/RestfulClientService";
import { writeApiEndpoint } from "../service/Endpoints";

function CreateAccount() {
    const [name, setName] = useState("");
    const [balance, setBalance] = useState(0.0);
    const history = useHistory();

    function create() {
        console.log("create account");
        postData(
            writeApiEndpoint + "/accounts",
            {
              "accountName": name,
              "openingBalance": balance
            },
            data => {
               // history.push("/myAccounts");
            },
            null
        );
    }

    return (
        <div>
            <div className="row">
                <div className="col">
                    <h3>Create Account</h3>
                </div>
            </div>
            <div className="row table mt-1">
                <div className="col">
                    <form>
                        <div className="form-group">
                            <label>Name</label>
                            <input
                                className="form-control w-50"
                                value={name}
                                onChange={e => setName(e.target.value)}
                            />
                        </div>
                        <div className="form-group">
                            <label>Opening Balance</label>
                            <input
                                type="number"
                                step="0.01"
                                className="form-control w-50"
                                id="openingBalance"
                                value={balance}
                                onChange={e => setBalance(e.target.value)}
                            />
                        </div>
                        <button
                            className="btn btn-dark"
                            onClick={e => create()}
                        >
                            Submit
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
}

export default CreateAccount;
