import React, { useState } from 'react';
import { useHistory, Link} from "react-router-dom";

const DUMMY_DATA = [
    {
      name: '062-000 12345678',
      balance: '200.32'
    },
    {
      name: '062-000 56781234',
      balance: '80.15'
    },

]
function MyAccounts(){
   const history = useHistory();
   const [data,setData] = useState(DUMMY_DATA);
   //TODO call setData(xx) to set real data

   return(
   <div>
   <div className="row">
     <div className="col">
        <h3>My Accounts</h3>
     </div>
   </div>
   <div className="row table mt-1">
       <table className='w-100'>
         <tr>
           <th>Account</th>
           <th>Balance</th>
         </tr>
         {data.map((acc, index)=>{
            return (
                       <tr key={index}>
                              <td><Link to={"/account/"+ acc.name}>{acc.name}</Link></td>
                              <td>{acc.balance}</td>
                            </tr>
             )
         })}
       </table>
   </div>
   <div className="row">
           <button type="button" className="btn btn-dark mr-auto" onClick={e => history.push("/createAccount")}>Create Account</button>
      </div>
   </div>
   )
}

export default MyAccounts;