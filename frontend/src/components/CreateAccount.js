import React, { useState } from 'react';
import { useHistory} from "react-router-dom";

function CreateAccount(){

   const [name, setName] = useState();
   const [balance, setBalance] = useState(0.00);
   const history = useHistory();

   function create(){
    // call the create account API with name and balance;
    //go back to My Accounts which should fetch the latest account list including this newly created one.
    history.push("/myAccounts")
   }


   return(
   <div>
    <div className="row">
         <div className="col">
            <h3>Create Account</h3>
         </div>
       </div>
       <div className="row table mt-1">
        <div className="col">
       <form>
         <div class="form-group">
           <label for="name">Name</label>
           <input class="form-control w-50" id="name" value={name} onChange={e=>setName(e.target.value)}/>
         </div>
         <div class="form-group">
           <label for="openingBalance">Opening Balance</label>
           <input type="number" step="0.01" class="form-control w-50" id="openingBalance" value={balance} onChange={e=>setBalance(e.target.value)}/>
         </div>
         <button class="btn btn-dark" onClick={e=>create()}>Submit</button>
       </form>
       </div>
       </div>
   </div>
   )
}

export default CreateAccount;