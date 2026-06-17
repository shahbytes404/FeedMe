import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserListPanel } from './user-list-panel';

describe('UserListPanel', () => {
  let component: UserListPanel;
  let fixture: ComponentFixture<UserListPanel>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserListPanel],
    }).compileComponents();

    fixture = TestBed.createComponent(UserListPanel);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
