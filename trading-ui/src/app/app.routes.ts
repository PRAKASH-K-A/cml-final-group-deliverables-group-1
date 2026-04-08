import { Routes } from '@angular/router';
import { OrderGridComponent } from './components/order-grid/order-grid.component';

export const routes: Routes = [
  { path: '', component: OrderGridComponent },
  { path: '**', redirectTo: '' }
];
