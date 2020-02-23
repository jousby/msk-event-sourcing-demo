import React, { useState , useEffect } from 'react';

const DUMMY_DATA = [
    {
      amount: 100.32,
      date: '5 Jan 2020'
    },
    {
      amount: 55.00,
      date: '2 Jan 2020'
    },
    {
      amount: 102.00,
      date: '1 Jan 2020'
    },
    {
      amount: 33.84,
      date: '1 Jan 2020'
    },

]

const DEPOSIT_ACTION = 'Deposit';
const WITHDRAW_ACTION = 'WithDraw';

function Account(props){
   const account = props.match.params.id;
   const [data,setData] = useState(DUMMY_DATA);
   const [showAction, setShowAction] = useState(false);
   const [amount, setAmount] = useState(0.00);
   const [action, setAction] = useState(DEPOSIT_ACTION);
      //TODO call setData(xx) to set real data


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
      //TODO submit deposit/withdraw request

      //TODO reload account transactions
   }

   function cancel(){
      setShowAction(false);
   }

   return(
   <div>
   <div className="row mb-3">
        <div className="col">
           <h3>Account</h3>{account}
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
               <tr>
                 <th>Date</th>
                 <th>Amount</th>
               </tr>
               {data.map((transaction, index)=>{
                  return (
                             <tr key={index}>
                                    <td>{transaction.date}</td>
                                    <td>{transaction.amount}</td>
                                  </tr>
                   )
               })}
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
