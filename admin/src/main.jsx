import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Navigate, NavLink, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { api, auth } from './api';
import { Loading, ToastProvider } from './components/ui';
import { AdminCtx } from './adminContext';
import './styles.css';

import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Products from './pages/Products';
import ProductForm from './pages/ProductForm';
import BulkUpload from './pages/BulkUpload';
import Categories from './pages/Categories';
import Orders from './pages/Orders';
import OrderDetail from './pages/OrderDetail';
import Coupons from './pages/Coupons';
import Banners from './pages/Banners';
import Customers from './pages/Customers';
import CustomerDetail from './pages/CustomerDetail';
import Reports from './pages/Reports';
import Settings from './pages/Settings';
import Stores from './pages/Stores';
import Reviews from './pages/Reviews';
import Messages from './pages/Messages';
import Content from './pages/Content';


const NAV = [
  { group: 'Overview', items: [['/', 'Dashboard', '▦']] },
  { group: 'Catalog', items: [['/products', 'Products', '▤'], ['/products/import', 'Bulk upload', '⇪'], ['/categories', 'Categories', '◫'], ['/stores', 'Stores', '⌂']] },
  { group: 'Sales', items: [['/orders', 'Orders', '▣'], ['/customers', 'Customers', '◉'], ['/reviews', 'Reviews', '★'], ['/messages', 'Messages', '✉'], ['/reports', 'Reports', '◔']] },
  { group: 'Marketing', items: [['/coupons', 'Coupons', '%'], ['/banners', 'Banners', '▭'], ['/content', 'App content', '❏']] },
  { group: 'System', items: [['/settings', 'Settings', '⚙']] },
];

function Shell() {
  const [admin, setAdmin] = useState(null);
  const [open, setOpen] = useState(false);
  const nav = useNavigate();
  const loc = useLocation();
  useEffect(() => { setOpen(false); }, [loc.pathname]);
  useEffect(() => {
    if (!auth.token) { nav('/login', { replace: true }); return; }
    api.get('/auth/me').then(setAdmin).catch(() => {});
  }, [nav]);
  if (!auth.token) return <Navigate to="/login" replace />;
  if (!admin) return <Loading />;
  return (
    <AdminCtx.Provider value={admin}>
      <div className="mobile-bar"><button onClick={() => setOpen(!open)} aria-label="Menu">☰</button><strong>Store Admin</strong></div>
      <div className="shell">
        <aside className={`side ${open ? 'open' : ''}`}>
          <div className="logo"><i />Store Admin</div>
          <nav>
            {NAV.map((g) => (
              <div key={g.group}>
                <div className="group">{g.group}</div>
                {g.items.map(([to, text, icon]) => (
                  <NavLink key={to} to={to} end={to === '/' || to === '/products'}><span style={{ width: 16, textAlign: 'center' }}>{icon}</span>{text}</NavLink>
                ))}
              </div>
            ))}
          </nav>
          <div className="me">
            <div style={{ color: '#fff' }}>{admin.name}</div>
            <div className="muted" style={{ color: '#7b8a89' }}>{admin.email}</div>
            <button className="btn sm" style={{ marginTop: 10 }} onClick={() => { auth.clear(); nav('/login'); }}>Log out</button>
          </div>
        </aside>
        <main className="main"><Outlet /></main>
      </div>
    </AdminCtx.Provider>
  );
}

function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route element={<Shell />}>
            <Route path="/" element={<Dashboard />} />
            <Route path="/products" element={<Products />} />
            <Route path="/products/new" element={<ProductForm />} />
            <Route path="/products/import" element={<BulkUpload />} />
            <Route path="/products/:id" element={<ProductForm />} />
            <Route path="/categories" element={<Categories />} />
            <Route path="/orders" element={<Orders />} />
            <Route path="/orders/:id" element={<OrderDetail />} />
            <Route path="/customers" element={<Customers />} />
            <Route path="/customers/:id" element={<CustomerDetail />} />
            <Route path="/coupons" element={<Coupons />} />
            <Route path="/banners" element={<Banners />} />
            <Route path="/reports" element={<Reports />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/stores" element={<Stores />} />
            <Route path="/reviews" element={<Reviews />} />
            <Route path="/messages" element={<Messages />} />
            <Route path="/content" element={<Content />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </ToastProvider>
    </BrowserRouter>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
