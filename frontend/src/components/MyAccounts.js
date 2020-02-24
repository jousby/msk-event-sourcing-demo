import React, { useState, useEffect } from "react";
import { useHistory, Link } from "react-router-dom";
import {getData} from "../service/RestfulClientService";

function MyAccounts() {
    const history = useHistory();
    const [data, setData] = useState([]);

    useEffect(()=>{
       loadData();
    },[]);
    
    function loadData (){
        getData("http://localhost:4568/accounts",data=>{
            setData(data);
        },null);
    }

    return (
        <div>
            <div className="row">
                <div className="col">
                    <h3>My Accounts</h3>
                </div>
            </div>
            <div className="row table mt-1">
                <table className="w-100">
                    <thead>
                    <tr>
                        <th>Account</th>
                        <th>Balance</th>
                    </tr>
                    </thead>
                    <tbody>
                    {data.map((acc, index) => {
                        return (
                            <tr key={index}>
                                <td>
                                    <Link to={"/account/" + acc.accountName}>
                                        {acc.accountName}
                                    </Link>
                                </td>
                                <td>{acc.balance.toFixed(2)}</td>
                            </tr>
                        );
                    })}
                    </tbody>
                </table>
            </div>
            <div className="row">
            <div className="col">
                <button
                    type="button"
                    className="btn btn-dark mr-auto"
                    onClick={e => history.push("/createAccount")}
                >
                    Create Account
                </button>
                </div>
            </div>
        </div>
    );
}

export default MyAccounts;
