sig Time in Int {}
one sig BankAccount
{
    deposit: Int one -> Time,
    withdrawal: Int one-> Time,
    balance: Int one-> Time
}
{#deposit > 0 and #withdrawal > 0 and #balance > 0 }
fun depositValue[t: Time]: Int {BankAccount.deposit.t}
fun withdrawalValue[t: Time]: Int {BankAccount.withdrawal.t}
fun balanceValue[t: Time]: Int {BankAccount.balance.t}
pred deposit[t, t' : Time, amount: Int]
{
    amount > 0
    depositValue[t'] = amount
    withdrawalValue[t'] = 0
    balanceValue[t'] = plus[balanceValue[t], amount]
}
pred withdraw[t, t' : Time, amount: Int]
{
    amount > 0
    balanceValue[t] >= amount -- there is enough balance at t
    depositValue[t'] = 0
    withdrawalValue[t'] = amount
    balanceValue[t'] = minus[balanceValue[t], amount]
}
assert sanity
{
    all  t': Time - 0, a : univInt |
    let t = minus[t',1] |
    {
        a > 0 and withdraw[t, t', a]  and balanceValue[t] >= a
        implies
        balanceValue[t'] < balanceValue[t]
    }
}
check sanity
//pred init [t: Time]
//{
//  BankAccount.deposit.t = 0
//  BankAccount.withdrawal.t = 0
//  BankAccount.balance.t = 0
//}
//
//pred someTransaction[t, t': Time]
//{
//  some amount : Int | deposit[t, t', amount] or withdraw[t, t', amount]
//}
//
//
//pred system
//{
//  init[0]
//  all t: Time - 0 | someTransaction[minus[t, 1] , t]
//}
//
//run scenario
//{
//  init[0]
//  deposit[0, 1, 10]
//  deposit[1, 2, 40]
//  withdraw[2, 3, 30]
//} for 7 Int
//
//pred nonNegative [t: Time]
//{
//  depositValue[t] >= 0 and
//  withdrawalValue[t] >= 0 and
//  balanceValue[t] >= 0
//}
//
