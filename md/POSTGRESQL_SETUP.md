# PostgreSQL Setup Guide for Windows

## Step 1: Download PostgreSQL

1. **Visit**: https://www.postgresql.org/download/windows/
2. **Download**: PostgreSQL installer (recommended version: 15 or 16)
3. **Alternative**: Use the EDB installer: https://www.enterprisedb.com/downloads/postgres-postgresql-downloads

## Step 2: Install PostgreSQL

Run the installer and configure:

```
Port: 5432 (default)
Username: postgres
Password: [Choose a strong password - remember this!]
```

**IMPORTANT**: Write down your password! You'll need it for connection strings.

During installation:
- ✅ Install PostgreSQL Server
- ✅ Install pgAdmin 4 (GUI tool)
- ✅ Install Command Line Tools
- ❌ Stack Builder (optional, skip for now)

## Step 3: Verify Installation

Open PowerShell and test:

```powershell
# Check if PostgreSQL is running
Get-Service -Name postgresql*

# Expected output: Status should be "Running"
```

## Step 4: Connect via Command Line

```powershell
# Connect to PostgreSQL (it will prompt for password)
psql -U postgres

# You should see:
# postgres=#
```

If `psql` is not recognized, add to PATH:
```
C:\Program Files\PostgreSQL\15\bin
```

## Step 5: Create Trading Database

In the `psql` prompt:

```sql
-- Create the database
CREATE DATABASE trading_system;

-- Connect to it
\c trading_system

-- Create the orders table
CREATE TABLE orders (
    order_id VARCHAR(50) PRIMARY KEY,
    cl_ord_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side CHAR(1) NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    quantity DECIMAL(15, 0) NOT NULL,
    status VARCHAR(20) DEFAULT 'NEW',
    timestamp TIMESTAMPTZ DEFAULT NOW()
);

-- Create indexes for fast queries
CREATE INDEX idx_orders_symbol ON orders(symbol);
CREATE INDEX idx_orders_timestamp ON orders(timestamp DESC);
CREATE INDEX idx_orders_status ON orders(status);

-- Verify table creation
\dt

-- Describe the table structure
\d orders

-- Exit
\q
```

## Step 6: Using pgAdmin 4 (GUI Alternative)

1. Open **pgAdmin 4** from Start Menu
2. Create master password (one-time setup)
3. Expand **Servers** → **PostgreSQL**
4. Right-click **Databases** → **Create** → **Database**
5. Name: `trading_system`
6. Use the Query Tool to run the SQL from Step 5

## Step 7: Update Your Java Connection String

Your connection URL will be:
```
jdbc:postgresql://localhost:5432/trading_system
User: postgres
Password: [your chosen password]
```

## Quick Reference Commands

```powershell
# Start PostgreSQL (if stopped)
Start-Service postgresql-x64-15

# Stop PostgreSQL
Stop-Service postgresql-x64-15

# Restart PostgreSQL
Restart-Service postgresql-x64-15

# Connect to database
psql -U postgres -d trading_system

# View all tables
\dt

# View table structure
\d orders

# Run SQL file
psql -U postgres -d trading_system -f script.sql
```

## PostgreSQL vs MySQL Differences

| Feature | MySQL | PostgreSQL |
|---------|-------|------------|
| Connection URL | `jdbc:mysql://localhost:3306/db` | `jdbc:postgresql://localhost:5432/db` |
| Auto Increment | `AUTO_INCREMENT` | `SERIAL` or `GENERATED ALWAYS` |
| Current Time | `NOW()` | `NOW()` or `CURRENT_TIMESTAMP` |
| String Concat | `CONCAT()` | `\|\|` or `CONCAT()` |
| Case Sensitive | No | Yes (by default) |
| Date/Time | `TIMESTAMP` | `TIMESTAMPTZ` (timezone aware) |

## Troubleshooting

### "psql is not recognized"
Add to PATH: `C:\Program Files\PostgreSQL\15\bin`

### "Connection refused"
```powershell
# Check if PostgreSQL is running
Get-Service -Name postgresql*

# If stopped, start it
Start-Service postgresql-x64-15
```

### "password authentication failed"
Reset password:
```powershell
# 1. Open pg_hba.conf (usually in C:\Program Files\PostgreSQL\15\data)
# 2. Change 'md5' to 'trust' temporarily
# 3. Restart PostgreSQL
# 4. Connect and change password:
psql -U postgres
ALTER USER postgres PASSWORD 'new_password';
# 5. Change 'trust' back to 'md5' in pg_hba.conf
# 6. Restart PostgreSQL
```

## Next Steps

After completing setup:
1. ✅ PostgreSQL is installed and running
2. ✅ Database `trading_system` is created
3. ✅ Table `orders` is ready
4. ✅ You know your connection credentials

Now proceed with Lab 5 implementation!
