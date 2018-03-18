import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { HomeComponent } from './components/home/home.component';
import { TransactionDetailsComponent } from './components/transaction-details/transaction-details.component';
import { AddressDetailsComponent } from './components/address-details/address-details.component';

const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'addresses/:address', component: AddressDetailsComponent },
  { path: 'transactions/:txid', component: TransactionDetailsComponent },
  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [
    RouterModule.forRoot(routes)
  ],
  exports: [
    RouterModule
  ]
})
export class AppRoutingModule {

}
