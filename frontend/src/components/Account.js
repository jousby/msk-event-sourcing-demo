import React, { useState , useEffect } from 'react';
import {getData, postData} from "../service/RestfulClientService";
import SystemMessageStore, { MessageType } from '../store/SystemMessageStore';

const DEPOSIT_ACTION = 'Deposit';
const WITHDRAW_ACTION = 'WithDraw';

function Account(props){
   const accName = props.match.params.id;
   const [account, setAccount] = useState({balance: 0.00});
   const [data,setData] = useState([]);
   const [showAction, setShowAction] = useState(false);
   const [amount, setAmount] = useState(0.00);
   const [action, setAction] = useState(DEPOSIT_ACTION);
      
   useEffect(()=>{
    loadData();
 },[]);
 
 function loadData (){
  getData("http://localhost:4568/accounts/"+accName, data=>{
    setAccount(data);
},null);
     getData("http://localhost:4568/accounts/"+accName+"/transactions",data=>{
         setData(data);
     },null);
 }


   function deposit(){
     setAction(DEPOSIT_ACTION);
     setShowAction(true);
   }

   function withdraw(){
     setAction(WITHDRAW_ACTION);
     setShowAction(true);
   }

   function submit(){
      setShowAction(false);
      let url = "http://localhost:4567/accounts/"+accName+"/"
      if (action === DEPOSIT_ACTION){
         url += 'deposit?amount='+amount+'&version='+account.version;
      }else {
        url += 'withdraw?amount='+amount+'&version='+account.version;
      };
      postData(url, {}, data=>{
        loadData();
      }, error=>{
        console.log("error found");
        SystemMessageStore.setMessage(MessageType.ERROR, error);
      });
      setAmount(0.00);
   }

   function cancel(){
      setShowAction(false);
   }

   return(
   <div>
   <div className="row mb-3">
        <div className="col">
           <h3>Account</h3>{accName} <label className='ml-2'>{account.balance.toFixed(2)}</label>
        </div>
      </div>
       <div className="row">
              <div className="col">
                 <h3>Transaction History</h3>
              </div>
            </div>
       <div className="row table mt-1">
        <div className="col">
             <table className='w-100'>
               <thead>
               <tr>
                 <th>Date</th>
                 <th>Amount</th>
               </tr>
               </thead>
               <tbody>
               {data.map((transaction, index)=>{
                  return (
                             <tr key={index}>
                                    <td>{transaction.ts.seconds}</td>
                                    <td>{transaction.amount.toFixed(2)}</td>
                                  </tr>
                   )
               })}
               </tbody>
             </table>
             </div>
         </div>
          {!showAction &&
              <div className="row">
               <div className="col">
                        <button type="button" className="btn btn-success mr-2" onClick={e => deposit()}>Deposit</button>
                        <button type="button" className="btn btn-danger mr-auto" onClick={e => withdraw()}>Withdraw</button>
                        </div>
                </div>
            }
            {showAction &&
            <div className="row">
                    <div className="col col-2">
                           <label><h4>{action + ' Amount'}</h4></label>
                     </div>
                     <div className="col col-3">
                          <input type="number" step="0.01" className="form-control w-100" value={amount} onChange={e=>setAmount(e.target.value)}/>
                     </div>
                     <div className="col col-7">
                                <button type="button" className="btn btn-dark mr-2" onClick={e => submit()}>Submit</button>
                                <button type="button" className="btn btn-secondary mr-auto" onClick={e => cancel()}>Cancel</button>
                     </div>
            </div>
            }
    </div>
   )
}

export default Account;
